package com.athletebridge.android.testutil

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.SnapshotMetadata
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import java.util.Date

/**
 * In-memory stand-in for [FirebaseFirestore], assembled from MockK mocks, so the real
 * repository code can run against realistic iOS- and Android-shaped documents.
 *
 * Follows Firestore semantics where the app depends on them:
 *  - update() on a missing document fails with NOT_FOUND; batches and transactions are atomic
 *  - dotted field paths in update(); set() replaces unless SetOptions.merge() is passed
 *  - FieldValue.delete / serverTimestamp / increment / arrayUnion / arrayRemove
 *  - where/orderBy exclude documents that lack the field; empty document ids are rejected
 *  - snapshot listeners fire once, immediately, with the current results
 */
class FakeFirestore(var serverTime: Timestamp = Timestamp(Date(1_800_000_000_000L))) {

    data class Write(val op: String, val path: String, val fields: Map<String, Any?>)

    /** Every applied write, in order (rolled back with a failed batch/transaction). */
    val writes = mutableListOf<Write>()
    var batchCommits = 0
        private set

    val instance: FirebaseFirestore = mockk()

    private val store = linkedMapOf<String, MutableMap<String, Any?>>()
    private val docRefs = mutableMapOf<String, DocumentReference>()
    private val colRefs = mutableMapOf<String, CollectionReference>()
    private var autoIds = 0
    private val registration: ListenerRegistration = mockk(relaxed = true)
    private val metadata: SnapshotMetadata = mockk {
        every { isFromCache } returns false
        every { hasPendingWrites() } returns false
    }

    init {
        every { instance.collection(any()) } answers { collection(firstArg()) }
        every { instance.document(any()) } answers { document(firstArg()) }
        every { instance.batch() } answers { newBatch() }
        every { instance.runBatch(any()) } answers {
            val batch = newBatch()
            firstArg<WriteBatch.Function>().apply(batch)
            batch.commit()
        }
        every { instance.runTransaction(any<Transaction.Function<Any?>>()) } answers {
            runTransaction(firstArg())
        }
    }

    // ── Test-facing helpers ─────────────────────────────────────────────────

    /** Put a document in the store as if it already existed on the server. */
    fun seed(path: String, data: Map<String, Any?>) {
        store[path] = resolveForSet(data)
    }

    /** Current contents of a document, or null if it doesn't exist. */
    fun doc(path: String): Map<String, Any?>? = store[path]

    /** Documents directly inside a collection, keyed by document id. */
    fun docsIn(collectionPath: String): Map<String, Map<String, Any?>> =
        store.filterKeys { parentOf(it) == collectionPath }
            .mapKeys { it.key.substringAfterLast('/') }

    fun ref(path: String): DocumentReference = document(path)

    // ── References ──────────────────────────────────────────────────────────

    fun document(path: String): DocumentReference {
        val segments = path.split('/')
        require(segments.size % 2 == 0 && segments.all { it.isNotEmpty() }) {
            "Invalid document reference. Document references must have an even number of segments, but $path has ${segments.size}"
        }
        return docRefs.getOrPut(path) { newDocRef(path) }
    }

    fun collection(path: String): CollectionReference {
        val segments = path.split('/')
        require(segments.size % 2 == 1 && segments.all { it.isNotEmpty() }) {
            "Invalid collection reference: $path"
        }
        return colRefs.getOrPut(path) { newCollectionRef(path) }
    }

    private fun nextId() = "auto${++autoIds}"

    private fun parentOf(docPath: String) = docPath.substringBeforeLast('/')

    private fun newDocRef(path: String): DocumentReference {
        val ref = mockk<DocumentReference>()
        every { ref.id } returns path.substringAfterLast('/')
        every { ref.path } returns path
        every { ref.parent } answers { collection(path.substringBeforeLast('/')) }
        every { ref.collection(any()) } answers { collection("$path/${firstArg<String>()}") }
        every { ref.get() } answers { Tasks.forResult(snapshot(path)) }
        every { ref.get(any()) } answers { Tasks.forResult(snapshot(path)) }
        every { ref.set(any()) } answers { voidTask { applySet(path, firstArg(), merge = false) } }
        every { ref.set(any(), any()) } answers {
            voidTask { applySet(path, firstArg(), merge = secondArg<SetOptions>() == SetOptions.merge()) }
        }
        every { ref.update(any<Map<String, Any>>()) } answers { voidTask { applyUpdate(path, firstArg()) } }
        every { ref.update(any<String>(), any(), *anyVararg()) } answers {
            voidTask { applyUpdate(path, pairsFrom(args)) }
        }
        every { ref.delete() } answers { voidTask { applyDelete(path) } }
        every { ref.addSnapshotListener(any<EventListener<DocumentSnapshot>>()) } answers {
            firstArg<EventListener<DocumentSnapshot>>().onEvent(snapshot(path), null)
            registration
        }
        return ref
    }

    private fun newCollectionRef(path: String): CollectionReference {
        val col = mockk<CollectionReference>()
        every { col.id } returns path.substringAfterLast('/')
        every { col.path } returns path
        every { col.document() } answers { document("$path/${nextId()}") }
        every { col.document(any()) } answers { document("$path/${firstArg<String>()}") }
        every { col.add(any()) } answers {
            val ref = document("$path/${nextId()}")
            try {
                applySet(ref.path, firstArg(), merge = false)
                Tasks.forResult(ref)
            } catch (e: Exception) {
                Tasks.forException(e)
            }
        }
        stubQuery(col, QuerySpec(path))
        return col
    }

    // ── Snapshots ───────────────────────────────────────────────────────────

    fun snapshot(path: String): DocumentSnapshot {
        val stored = store[path]?.let { deepCopyMap(it) }
        val snap = mockk<DocumentSnapshot>()
        every { snap.id } returns path.substringAfterLast('/')
        @Suppress("UNCHECKED_CAST")
        every { snap.data } returns (stored as Map<String, Any>?)
        // Stored docs have no pending server timestamps, so every behavior reads the same data
        @Suppress("UNCHECKED_CAST")
        every { snap.getData(any()) } returns (stored as Map<String, Any>?)
        every { snap.exists() } returns (stored != null)
        every { snap.getString(any()) } answers { stored?.get(firstArg<String>()) as? String }
        every { snap.get(any<String>()) } answers { stored?.get(firstArg<String>()) }
        every { snap.reference } answers { document(path) }
        every { snap.metadata } returns metadata
        return snap
    }

    private fun querySnapshot(spec: QuerySpec): QuerySnapshot {
        val docs = evaluate(spec).map { snapshot(it) }
        val qs = mockk<QuerySnapshot>()
        every { qs.documents } returns docs
        every { qs.isEmpty } returns docs.isEmpty()
        every { qs.size() } returns docs.size
        every { qs.metadata } returns metadata
        return qs
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    private data class QuerySpec(
        val collection: String,
        val filters: List<(String, Map<String, Any?>) -> Boolean> = emptyList(),
        val orders: List<Pair<String, Query.Direction>> = emptyList(),
        val limit: Long? = null,
        val startAfterId: String? = null
    )

    private fun newQuery(spec: QuerySpec): Query = mockk<Query>().also { stubQuery(it, spec) }

    private fun stubQuery(q: Query, spec: QuerySpec) {
        fun filtered(filter: (String, Map<String, Any?>) -> Boolean) =
            newQuery(spec.copy(filters = spec.filters + filter))

        fun range(field: String, bound: Any?, test: (Int) -> Boolean) =
            filtered { _, d -> compareFirestore(d[field], bound)?.let(test) ?: false }

        every { q.whereEqualTo(any<String>(), any()) } answers {
            val field = firstArg<String>()
            val value = secondArg<Any?>()
            filtered { _, d -> d.containsKey(field) && valuesEqual(d[field], value) }
        }
        every { q.whereArrayContains(any<String>(), any()) } answers {
            val field = firstArg<String>()
            val value = secondArg<Any?>()
            filtered { _, d -> (d[field] as? List<*>)?.any { valuesEqual(it, value) } == true }
        }
        every { q.whereIn(any<FieldPath>(), any()) } answers {
            require(firstArg<FieldPath>() == FieldPath.documentId()) { "FakeFirestore: only whereIn(documentId()) is supported" }
            val ids = secondArg<List<Any?>>()
            filtered { id, _ -> ids.contains(id) }
        }
        every { q.whereGreaterThan(any<String>(), any()) } answers { range(firstArg(), secondArg()) { it > 0 } }
        every { q.whereGreaterThanOrEqualTo(any<String>(), any()) } answers { range(firstArg(), secondArg()) { it >= 0 } }
        every { q.whereLessThan(any<String>(), any()) } answers { range(firstArg(), secondArg()) { it < 0 } }
        every { q.orderBy(any<String>()) } answers {
            newQuery(spec.copy(orders = spec.orders + (firstArg<String>() to Query.Direction.ASCENDING)))
        }
        every { q.orderBy(any<String>(), any()) } answers {
            newQuery(spec.copy(orders = spec.orders + (firstArg<String>() to secondArg<Query.Direction>())))
        }
        every { q.limit(any()) } answers { newQuery(spec.copy(limit = firstArg())) }
        every { q.startAfter(any<DocumentSnapshot>()) } answers {
            newQuery(spec.copy(startAfterId = firstArg<DocumentSnapshot>().id))
        }
        every { q.get() } answers { Tasks.forResult(querySnapshot(spec)) }
        every { q.get(any()) } answers { Tasks.forResult(querySnapshot(spec)) }
        every { q.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } answers {
            firstArg<EventListener<QuerySnapshot>>().onEvent(querySnapshot(spec), null)
            registration
        }
    }

    private fun evaluate(spec: QuerySpec): List<String> {
        var paths = store.keys
            .filter { parentOf(it) == spec.collection }
            .filter { p -> spec.filters.all { f -> f(p.substringAfterLast('/'), store.getValue(p)) } }
        // orderBy silently excludes documents missing the field, like Firestore
        for ((field, _) in spec.orders) paths = paths.filter { store.getValue(it)[field] != null }
        if (spec.orders.isNotEmpty()) {
            val comparator = spec.orders.map { (field, dir) ->
                Comparator<String> { a, b ->
                    val c = compareFirestore(store.getValue(a)[field], store.getValue(b)[field]) ?: 0
                    if (dir == Query.Direction.DESCENDING) -c else c
                }
            }.reduce { acc, c -> acc.then(c) }
            paths = paths.sortedWith(comparator)
        }
        spec.startAfterId?.let { id ->
            val idx = paths.indexOfFirst { it.substringAfterLast('/') == id }
            if (idx >= 0) paths = paths.drop(idx + 1)
        }
        spec.limit?.let { paths = paths.take(it.toInt()) }
        return paths
    }

    private fun compareFirestore(a: Any?, b: Any?): Int? = when {
        a is Timestamp && b is Timestamp -> a.compareTo(b)
        a is Number && b is Number -> a.toDouble().compareTo(b.toDouble())
        a is String && b is String -> a.compareTo(b)
        a is Boolean && b is Boolean -> a.compareTo(b)
        else -> null
    }

    private fun valuesEqual(a: Any?, b: Any?): Boolean =
        if (a is Number && b is Number) a.toDouble() == b.toDouble() else a == b

    // ── Writes ──────────────────────────────────────────────────────────────

    private fun applySet(path: String, data: Any?, merge: Boolean) {
        val map = toFieldMap(data)
        val result = if (merge) mergeInto(store[path]?.let { deepCopyMap(it) } ?: linkedMapOf(), map)
        else resolveForSet(map)
        writes += Write(if (merge) "merge" else "set", path, map)
        store[path] = result
    }

    private fun applyUpdate(path: String, updates: Map<String, Any?>) {
        val existing = store[path] ?: throw FirebaseFirestoreException(
            "NOT_FOUND: No document to update: $path", FirebaseFirestoreException.Code.NOT_FOUND
        )
        val copy = deepCopyMap(existing)
        for ((fieldPath, value) in updates) applyField(copy, fieldPath.split('.'), value)
        writes += Write("update", path, updates)
        store[path] = copy
    }

    private fun applyDelete(path: String) {
        writes += Write("delete", path, emptyMap())
        store.remove(path)
    }

    private fun newBatch(): WriteBatch {
        val ops = mutableListOf<() -> Unit>()
        val batch = mockk<WriteBatch>()
        every { batch.set(any(), any()) } answers {
            val ref = firstArg<DocumentReference>(); val data = secondArg<Any?>()
            ops += { applySet(ref.path, data, merge = false) }; batch
        }
        every { batch.set(any(), any(), any()) } answers {
            val ref = firstArg<DocumentReference>(); val data = secondArg<Any?>()
            val merge = thirdArg<SetOptions>() == SetOptions.merge()
            ops += { applySet(ref.path, data, merge) }; batch
        }
        every { batch.update(any(), any<Map<String, Any>>()) } answers {
            val ref = firstArg<DocumentReference>(); val updates = secondArg<Map<String, Any?>>()
            ops += { applyUpdate(ref.path, updates) }; batch
        }
        every { batch.update(any(), any<String>(), any(), *anyVararg()) } answers {
            val ref = firstArg<DocumentReference>(); val updates = pairsFrom(args.drop(1))
            ops += { applyUpdate(ref.path, updates) }; batch
        }
        every { batch.delete(any()) } answers {
            val ref = firstArg<DocumentReference>()
            ops += { applyDelete(ref.path) }; batch
        }
        every { batch.commit() } answers { commitAtomically(ops) }
        return batch
    }

    private fun runTransaction(fn: Transaction.Function<Any?>): Task<Any?> {
        val ops = mutableListOf<() -> Unit>()
        val txn = mockk<Transaction>()
        every { txn.get(any()) } answers { snapshot(firstArg<DocumentReference>().path) }
        every { txn.update(any(), any<Map<String, Any>>()) } answers {
            val ref = firstArg<DocumentReference>(); val updates = secondArg<Map<String, Any?>>()
            ops += { applyUpdate(ref.path, updates) }; txn
        }
        every { txn.update(any(), any<String>(), any(), *anyVararg()) } answers {
            val ref = firstArg<DocumentReference>(); val updates = pairsFrom(args.drop(1))
            ops += { applyUpdate(ref.path, updates) }; txn
        }
        every { txn.set(any(), any()) } answers {
            val ref = firstArg<DocumentReference>(); val data = secondArg<Any?>()
            ops += { applySet(ref.path, data, merge = false) }; txn
        }
        every { txn.delete(any()) } answers {
            val ref = firstArg<DocumentReference>()
            ops += { applyDelete(ref.path) }; txn
        }
        return try {
            val result = fn.apply(txn)
            val commit = commitAtomically(ops)
            if (commit.isSuccessful) Tasks.forResult(result) else Tasks.forException(commit.exception!!)
        } catch (e: Exception) {
            Tasks.forException(e)
        }
    }

    private fun commitAtomically(ops: List<() -> Unit>): Task<Void> {
        val before = store.mapValues { deepCopyMap(it.value) }
        val writesBefore = writes.size
        return try {
            ops.forEach { it() }
            batchCommits++
            voidResult()
        } catch (e: Exception) {
            store.clear()
            before.forEach { (k, v) -> store[k] = v }
            while (writes.size > writesBefore) writes.removeAt(writes.size - 1)
            Tasks.forException(e)
        }
    }

    // ── Field value resolution ─────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun toFieldMap(data: Any?): Map<String, Any?> =
        data as? Map<String, Any?> ?: error("FakeFirestore only supports Map data, got ${data?.javaClass}")

    private fun resolveForSet(map: Map<String, Any?>): MutableMap<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        for ((k, v) in map) {
            require(v !== FieldValue.delete()) { "FieldValue.delete() can only be used with update() and set() with merge" }
            out[k] = resolveValue(v, null)
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeInto(target: MutableMap<String, Any?>, data: Map<String, Any?>): MutableMap<String, Any?> {
        for ((k, v) in data) {
            val cur = target[k]
            when {
                v === FieldValue.delete() -> target.remove(k)
                v is Map<*, *> && cur is Map<*, *> -> target[k] = mergeInto(deepCopyMap(cur), v as Map<String, Any?>)
                else -> target[k] = resolveValue(v, cur)
            }
        }
        return target
    }

    private fun applyField(target: MutableMap<String, Any?>, parts: List<String>, value: Any?) {
        val key = parts.first()
        if (parts.size == 1) {
            if (value === FieldValue.delete()) target.remove(key) else target[key] = resolveValue(value, target[key])
            return
        }
        val child = (target[key] as? Map<*, *>)?.let { deepCopyMap(it) } ?: linkedMapOf()
        applyField(child, parts.drop(1), value)
        target[key] = child
    }

    private fun resolveValue(v: Any?, existing: Any?): Any? = when {
        v === FieldValue.serverTimestamp() -> serverTime
        isSentinel(v, "Increment") -> increment(existing, sentinelPart(v!!, Number::class.java) as Number)
        isSentinel(v, "ArrayUnion") -> {
            val cur = (existing as? List<*>).orEmpty()
            cur + (sentinelPart(v!!, List::class.java) as List<*>).filter { e -> cur.none { valuesEqual(it, e) } }
        }
        isSentinel(v, "ArrayRemove") -> {
            val remove = sentinelPart(v!!, List::class.java) as List<*>
            (existing as? List<*>).orEmpty().filter { e -> remove.none { valuesEqual(it, e) } }
        }
        v is Map<*, *> -> v.entries.associateTo(linkedMapOf<String, Any?>()) { (k, x) -> (k as String) to resolveValue(x, null) }
        v is List<*> -> v.map { resolveValue(it, null) }
        else -> v
    }

    private fun isSentinel(v: Any?, namePart: String) = v is FieldValue && v.javaClass.name.contains(namePart)

    /** Pull the operand / element list out of a FieldValue sentinel without depending on its exact private API. */
    private fun sentinelPart(v: Any, type: Class<*>): Any? {
        var cls: Class<*>? = v.javaClass
        while (cls != null && cls != Any::class.java) {
            cls.declaredFields.firstOrNull { type.isAssignableFrom(it.type) }?.let {
                it.isAccessible = true
                return it.get(v)
            }
            cls = cls.superclass
        }
        error("Unsupported FieldValue sentinel ${v.javaClass}")
    }

    private fun increment(existing: Any?, by: Number): Number = when {
        existing !is Number -> by
        existing is Double || existing is Float || by is Double || by is Float -> existing.toDouble() + by.toDouble()
        else -> existing.toLong() + by.toLong()
    }

    private fun pairsFrom(args: List<Any?>): Map<String, Any?> {
        val flat = args.flatMap { if (it is Array<*>) it.toList() else listOf(it) }
        return flat.chunked(2).associate { pair -> (pair[0] as String) to pair.getOrNull(1) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun voidResult(): Task<Void> = Tasks.forResult<Void?>(null) as Task<Void>

    private inline fun voidTask(block: () -> Unit): Task<Void> =
        try {
            block()
            voidResult()
        } catch (e: Exception) {
            Tasks.forException(e)
        }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun deepCopyMap(m: Map<*, *>): MutableMap<String, Any?> =
            m.entries.associateTo(linkedMapOf()) { (k, v) -> (k as String) to deepCopy(v) }

        private fun deepCopy(v: Any?): Any? = when (v) {
            is Map<*, *> -> deepCopyMap(v)
            is List<*> -> v.map { deepCopy(it) }
            else -> v
        }
    }
}
