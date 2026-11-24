package com.athletebridge.android.crossplatform

import com.athletebridge.android.service.NotificationService
import com.athletebridge.android.testutil.FakeFirestore
import org.junit.Assert.fail
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Date
import kotlin.math.abs

// Harness for the cross-platform contract tests (cross-platform/README.md). The iOS suite has the same pieces
// in AthleteBridgeTests/CrossPlatform/CrossPlatformSupport.swift; keep the two in step.

/** One story from contract.json: its checkpoints and the values both apps use for it. */
class Story(val name: String) {
    private val spec = ((Contract.json["stories"] as Map<*, *>)[name] as? Map<*, *>)
        ?: error("contract.json has no story '$name'")
    val checkpoints: List<String> = (spec["checkpoints"] as List<*>).map { it as String }
    private val values = spec["values"] as Map<*, *>

    fun str(key: String): String = values[key] as String
    fun dbl(key: String): Double = (values[key] as Number).toDouble()
    fun int(key: String): Int = (values[key] as Number).toInt()
    fun date(key: String): Date = Date.from(Instant.parse(str(key)))
    fun obj(key: String): Map<*, *> = values[key] as Map<*, *>
    fun strings(key: String): List<String> = (values[key] as List<*>).map { it as String }
    fun stringMap(key: String): Map<String, String> = obj(key).entries.associate { (k, v) -> k as String to v as String }
    fun expect(checkpoint: String): Map<*, *> = ((spec["expect"] as? Map<*, *>)?.get(checkpoint) as? Map<*, *>).orEmpty()
}

/** contract.json "actors": who plays each part in the stories. */
object Actors {
    private val spec = Contract.json["actors"] as Map<*, *>
    val keys: List<String> = spec.keys.map { it as String }
    private fun field(key: String, f: String) = (spec[key] as Map<*, *>)[f] as String
    fun name(key: String) = "${field(key, "first")} ${field(key, "last")}"
    fun role(key: String) = field(key, "role")
    /** The address tests sign up with; fixtures store it as {{<actor>-email}}. */
    fun email(key: String) = "${field(key, "first")}.${field(key, "last")}@example.com".lowercase()
}

/** What one app's real code wrote at each checkpoint of a story: only the documents that changed. */
class Fixture(val story: String, val writer: String, val checkpoints: List<Checkpoint>) {

    class Checkpoint(val name: String, val changed: Map<String, Map<String, Any?>?>)

    fun has(name: String) = checkpoints.any { it.name == name }

    /** Every document as it stood at [name]: setup and each later checkpoint applied in order. */
    fun state(name: String): Map<String, Map<String, Any?>> {
        require(has(name)) { "$writer's $story fixture has no '$name' checkpoint" }
        val docs = linkedMapOf<String, Map<String, Any?>>()
        for (cp in checkpoints) {
            for ((path, doc) in cp.changed) if (doc == null) docs.remove(path) else docs[path] = doc
            if (cp.name == name) break
        }
        return docs
    }

    fun changed(name: String): Map<String, Map<String, Any?>?> = checkpoints.first { it.name == name }.changed

    /** Pushes queued at [name] (pendingNotifications/{uid}/notifications docs created in that step). */
    fun pushes(name: String): List<Pair<String, Map<String, Any?>>> =
        changed(name).entries
            .filter { (path, doc) -> doc != null && path.startsWith("pendingNotifications/") && path.count { it == '/' } == 3 }
            .map { (path, doc) -> path.split('/')[1] to doc!! }

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "story" to story, "writer" to writer,
        "checkpoints" to checkpoints.map { linkedMapOf("name" to it.name, "changed" to it.changed) }
    )

    companion object {
        val recording: Boolean get() = System.getProperty("crossplatform.record") == "true"

        fun file(writer: String, story: String) = File(Contract.dir, "fixtures/$writer/$story.json")

        fun load(writer: String, story: String): Fixture? = file(writer, story).takeIf { it.isFile }?.let { parse(it.readText()) }

        @Suppress("UNCHECKED_CAST")
        fun parse(text: String): Fixture {
            val root = Json.parse(text) as Map<String, Any?>
            val cps = (root["checkpoints"] as List<*>).map { c ->
                c as Map<String, Any?>
                Checkpoint(c["name"] as String, c["changed"] as Map<String, Map<String, Any?>?>)
            }
            return Fixture(root["story"] as String, root["writer"] as String, cps)
        }
    }
}

// ── Recording ───────────────────────────────────────────────────────────────────────────────────

/** Ids written into documents, swapped for {{name}} placeholders so fixtures are the same on every run. */
internal object Ids {
    private val autoId = Regex("auto\\d+")                       // FakeFirestore
    private val firestoreId = Regex("[A-Za-z0-9]{20}")           // Firestore auto ids
    private val uuid = Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")

    /** A generated document id (only trusted where a document id goes: field names can be 20 letters too). */
    fun isDocId(s: String) = autoId.matches(s) || firestoreId.matches(s) || uuid.matches(s)
    /** A generated map key or value (signup keys, group ids). */
    fun isKeyId(s: String) = autoId.matches(s) || uuid.matches(s)

    /** Replace each raw id where it stands alone ("auto1" but not inside "auto12"), longest first. */
    fun replaceIn(s: String, ids: Map<String, String>): String {
        var out = s
        for ((raw, name) in ids.entries.sortedByDescending { it.key.length }) {
            if (!out.contains(raw)) continue
            val sb = StringBuilder()
            var i = 0
            while (true) {
                val j = out.indexOf(raw, i)
                if (j < 0) { sb.append(out, i, out.length); break }
                val before = out.getOrNull(j - 1)
                val after = out.getOrNull(j + raw.length)
                val alone = (before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())
                sb.append(out, i, j).append(if (alone) "{{$name}}" else raw)
                i = j + raw.length
            }
            out = sb.toString()
        }
        return out
    }

    fun replaceTree(v: Any?, ids: Map<String, String>): Any? = when (v) {
        is String -> replaceIn(v, ids)
        is Map<*, *> -> v.entries.associate { (k, x) -> replaceIn(k as String, ids) to replaceTree(x, ids) }
        is List<*> -> v.map { replaceTree(it, ids) }
        else -> v
    }

    fun replace(docs: Map<String, Any?>, ids: Map<String, String>): Map<String, Any?> =
        if (ids.isEmpty()) docs else docs.entries.associate { (p, d) -> replaceIn(p, ids) to replaceTree(d, ids) }
}

/**
 * Turns the raw database into fixture form: typed JSON; known ids (actors, the story's entities) as {{name}};
 * times written while the story ran as {{now:<step>}}, the checkpoint that first wrote them (so their order
 * survives: a reply is later than the message it answers); and every other generated id as {{h:xxxxxxxx}}, a hash
 * of where it first appeared, so the name is the same on every run and stays fixed once given (kept in [assigned]).
 */
internal class Normalizer(
    private val known: Map<String, String>,
    private val assigned: MutableMap<String, String>,
    private val firstSeen: MutableMap<String, String>,
    private val checkpoint: String,
    private val now: Instant = Instant.now()
) {
    @Suppress("UNCHECKED_CAST")
    fun normalize(raw: Map<String, Map<String, Any?>>): Map<String, Map<String, Any?>> {
        var docs: Map<String, Any?> = raw.mapValues { TypedJson.encode(it.value) }
        docs = Ids.replace(docs, known)
        docs = docs.mapValues { nowify(it.value) }
        docs = Ids.replace(docs, assigned)
        val fresh = generatedIds(docs)
        if (fresh.isNotEmpty()) {
            val mask = fresh.associateWith { "?" }
            val contexts = fresh.associateWith { contextOf(it, docs, mask) }
            val uses = mutableMapOf<String, Int>()
            for (g in fresh.sortedWith(compareBy({ contexts.getValue(it) }, { it }))) {
                val base = "h:" + sha1(contexts.getValue(g)).take(8)
                val n = (uses[base] ?: 0) + 1
                uses[base] = n
                assigned[g] = if (n == 1) base else "$base-$n"
            }
            docs = Ids.replace(docs, assigned.filterKeys { it in fresh })
        }
        return docs.toSortedMap() as Map<String, Map<String, Any?>>
    }

    private fun nowify(v: Any?): Any? = when (v) {
        is Map<*, *> ->
            if (TypedJson.isTagged(v, "\$timestamp") && near(v["\$timestamp"] as String))
                mapOf("\$timestamp" to "{{now:" + firstSeen.getOrPut(v["\$timestamp"] as String) { checkpoint } + "}}")
            else v.entries.associate { (k, x) -> k as String to nowify(x) }
        is List<*> -> v.map { nowify(it) }
        else -> v
    }

    private fun near(iso: String) =
        runCatching { abs(Instant.parse(iso).epochSecond - now.epochSecond) <= 2 * 86_400 }.getOrDefault(false)

    private fun generatedIds(docs: Map<String, Any?>): Set<String> {
        val out = linkedSetOf<String>()
        fun docIds(path: String) = path.split('/').filterIndexed { i, s -> i % 2 == 1 && !s.startsWith("{{") && Ids.isDocId(s) }
        for (path in docs.keys) out += docIds(path)
        val pathIds = out.toSet()
        fun scan(v: Any?) {
            when (v) {
                is String -> when {
                    v.startsWith("{{") -> {}
                    v in pathIds || Ids.isKeyId(v) -> out += v
                    v.count { it == '/' } % 2 == 1 -> out += docIds(v)   // a document path, e.g. a $ref
                }
                is Map<*, *> -> v.forEach { (k, x) -> if (Ids.isKeyId(k as String)) out += k; scan(x) }
                is List<*> -> v.forEach { scan(it) }
            }
        }
        docs.values.forEach { scan(it) }
        return out
    }

    /** Where [g] first shows up, with every other new id masked: the document it names, a map entry it keys, or a mention. */
    private fun contextOf(g: String, docs: Map<String, Any?>, mask: Map<String, String>): String {
        val found = mutableListOf<Pair<Int, String>>()
        for ((path, doc) in docs) {
            val masked = Ids.replaceIn(path, mask)
            val content = Json.compact(Ids.replaceTree(doc, mask))
            val segments = path.split('/')
            if (segments.last() == g) found += 0 to "doc|$masked|$content"
            else if (g in segments) found += 2 to "parent|$masked|$content"
            fun walk(v: Any?, at: String) {
                when (v) {
                    is Map<*, *> -> v.forEach { (k, x) ->
                        val key = k as String
                        if (key == g) found += 1 to "key|$masked|$at/{{?}}|" + Json.compact(Ids.replaceTree(x, mask))
                        walk(x, "$at/" + Ids.replaceIn(key, mask))
                    }
                    is List<*> -> v.forEachIndexed { i, x -> walk(x, "$at[$i]") }
                    is String -> if (Ids.replaceIn(v, mapOf(g to "?")) != v) found += 3 to "value|$masked|$at"
                }
            }
            walk(doc, "")
        }
        return found.minWithOrNull(compareBy({ it.first }, { it.second }))?.second ?: g
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * Records a story as the app's real code runs it: [checkpoint] snapshots the database after each step, and
 * [finish] either writes cross-platform/fixtures/android/<story>.json (record mode) or checks the committed
 * fixture still matches — if Android starts writing something different, iOS has to be re-checked against it.
 */
class Recorder(private val story: String, private val fake: FakeFirestore) {
    private val known = linkedMapOf<String, String>()
    private val assigned = linkedMapOf<String, String>()
    private val firstSeen = linkedMapOf<String, String>()
    private val checkpoints = mutableListOf<Fixture.Checkpoint>()
    private var previous: Map<String, Map<String, Any?>> = emptyMap()
    private val expected = Story(story).checkpoints

    /** Name an id the story created ("booking", "club", ...) so fixtures and the other app's tests can refer to it. */
    fun register(raw: String, name: String) {
        require(raw.isNotEmpty()) { "empty id for $name" }
        known[raw] = name
    }

    fun checkpoint(name: String) {
        require(name in expected) { "'$name' is not a checkpoint of '$story' in contract.json" }
        require(checkpoints.none { it.name == name }) { "checkpoint '$name' recorded twice" }
        val state = Normalizer(known, assigned, firstSeen, name).normalize(fake.allDocuments())
        val changed = linkedMapOf<String, Map<String, Any?>?>()
        for ((path, doc) in state) if (previous[path]?.let { Json.print(it) } != Json.print(doc)) changed[path] = doc
        for (path in previous.keys - state.keys) changed[path] = null
        checkpoints += Fixture.Checkpoint(name, changed)
        previous = state
    }

    fun finish() {
        val recorded = Fixture(story, "android", checkpoints)
        val file = Fixture.file("android", story)
        if (Fixture.recording) {
            file.parentFile.mkdirs()
            file.writeText(Json.print(recorded.toJson()))
            return
        }
        if (!file.isFile) fail("No Android fixture for '$story' yet. Record it with: cross-platform/check.sh --record")
        val problems = FixtureDiff.describe(Fixture.parse(file.readText()), recorded)
        if (problems.isNotEmpty()) fail(
            "Android now writes different data in the '$story' story than cross-platform/fixtures/android/$story.json, " +
                "which the iOS app was last checked against:\n  " + problems.joinToString("\n  ") +
                "\nIf the change is intended, re-record and re-check both apps with: cross-platform/check.sh --record"
        )
    }
}

/** Human-readable differences between two recordings of a story. */
object FixtureDiff {
    fun describe(old: Fixture, new: Fixture): List<String> {
        val out = mutableListOf<String>()
        val oldNames = old.checkpoints.map { it.name }
        val newNames = new.checkpoints.map { it.name }
        if (oldNames != newNames) out += "checkpoints were $oldNames, now $newNames"
        for (name in newNames.filter { it in oldNames }) {
            val a = old.changed(name)
            val b = new.changed(name)
            for (path in (a.keys + b.keys).toSortedSet()) {
                val x = a[path]
                val y = b[path]
                when {
                    path !in a -> out += "$name: now also writes $path"
                    path !in b -> out += "$name: no longer writes $path"
                    x == null || y == null -> if (x != y) out += "$name: $path " + (if (y == null) "now deleted" else "no longer deleted")
                    else -> {
                        val fx = flatten(x)
                        val fy = flatten(y)
                        for (k in (fx.keys + fy.keys).toSortedSet()) if (fx[k] != fy[k]) {
                            out += "$name: $path $k: ${fx[k] ?: "(missing)"} -> ${fy[k] ?: "(missing)"}"
                        }
                    }
                }
            }
        }
        return out.take(60)
    }

    private fun flatten(v: Any?, prefix: String = "", out: MutableMap<String, String> = sortedMapOf()): Map<String, String> {
        if (v is Map<*, *> && !(v.size == 1 && (v.keys.single() as String).startsWith("$"))) {
            if (v.isEmpty()) out[prefix.ifEmpty { "." }] = "{}"
            v.forEach { (k, x) -> flatten(x, if (prefix.isEmpty()) k as String else "$prefix.$k", out) }
        } else {
            out[prefix] = Json.compact(v)
        }
        return out
    }
}

// ── Reading another app's fixtures ──────────────────────────────────────────────────────────────

/**
 * Loads a fixture into a FakeFirestore, turning placeholders into concrete ids: actors get [uids], derived ids
 * (the chat id) are computed from them, everything else becomes "xp-<name>". {{now:<step>}} times become minutes
 * in the past in step order, so whatever the reading app writes next is later than everything in the fixture.
 */
class Seeder(private val fake: FakeFirestore, val uids: Map<String, String>) {
    private val derived = Contract.json["derivedIds"] as Map<*, *>
    private val base = Instant.now()
    private var steps: List<String> = emptyList()

    fun time(label: String): Instant {
        val step = label.removePrefix("{{now").removeSuffix("}}").removePrefix(":")
        val i = steps.indexOf(step)
        return if (i < 0) base.minusSeconds(60) else base.minusSeconds(60L * (steps.size + 1 - i))
    }

    fun id(name: String): String {
        uids[name]?.let { return it }
        if (name.endsWith("-email")) return Actors.email(name.removeSuffix("-email"))
        (derived[name] as? Map<*, *>)?.let { spec ->
            val parts = (spec["sortedJoin"] as List<*>).map { id(it as String) }.sorted()
            return parts.joinToString(spec["separator"] as String)
        }
        return "xp-" + name.replace(':', '-')
    }

    fun text(s: String): String = token.replace(s) { id(it.groupValues[1]) }

    fun tree(v: Any?): Any? = when (v) {
        is String -> text(v)
        is Map<*, *> ->
            if (TypedJson.isTagged(v, "\$timestamp") && (v["\$timestamp"] as String).startsWith("{{now"))
                mapOf("\$timestamp" to time(v["\$timestamp"] as String).toString())
            else v.entries.associate { (k, x) -> text(k as String) to tree(x) }
        is List<*> -> v.map { tree(it) }
        else -> v
    }

    /** A fixture document as the Android SDK would hand it to app code. */
    @Suppress("UNCHECKED_CAST")
    fun data(doc: Map<String, Any?>): Map<String, Any?> = TypedJson.decode(tree(doc)) { fake.ref(it) } as Map<String, Any?>

    fun seed(fixture: Fixture, checkpoint: String) {
        steps = fixture.checkpoints.map { it.name }
        for ((path, doc) in fixture.state(checkpoint)) fake.seed(text(path), data(doc))
    }

    companion object {
        val token = Regex("\\{\\{([^}]+)\\}\\}")
    }
}

/** onPendingNotificationCreate (functions/src/index.ts): what reaches the phone from a pendingNotifications doc. */
object CloudFunction {
    private val spec = Contract.json["cloudFunction"] as Map<*, *>
    val forwardedKeys: List<String> = (spec["forwardedKeys"] as List<*>).map { it as String }

    fun dataFor(notification: Map<String, Any?>, recipient: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (key in forwardedKeys) (notification[key] as? String)?.takeIf { it.isNotEmpty() }?.let { out[key] = it }
        out["recipientUid"] = recipient
        return out
    }
}

/** contract.json "pushRouting", checked against NotificationService.deepLinkFor. */
object PushRoutes {
    class Route(val type: String, val data: Map<String, String>, val accept: List<Pair<String, String?>>)

    val routes: List<Route> = ((Contract.json["pushRouting"] as Map<*, *>)["routes"] as List<*>).map { r ->
        r as Map<*, *>
        Route(
            r["type"] as String,
            (r["data"] as Map<*, *>).entries.associate { (k, v) -> k as String to v as String },
            (r["accept"] as List<*>).map { a -> a as Map<*, *>; a["screen"] as String to a["id"] as String? }
        )
    }

    /** The screen (and id) a tap opens on Android, in the contract's vocabulary. */
    fun androidScreen(data: Map<String, String>): Pair<String, String?>? {
        val link = NotificationService.deepLinkFor(data) ?: return null
        val parts = link.removePrefix("athletebridge://").split('/')
        val screen = when (parts[0]) {
            "accept_booking", "review_booking" -> "booking"
            "bookings" -> "bookings"
            "payments" -> "payments"
            "chat" -> "chat"
            "stringer_order_detail" -> "stringerOrder"
            "place_detail" -> "club"
            "my_clubs" -> "myClubs"
            "club_announcements" -> "clubAnnouncements"
            "signup_event_detail" -> "signupEvent"
            "signup_events" -> "signupEvents"
            "tournament_partner" -> "tournaments"
            else -> "unmapped link $link"
        }
        val id = if (screen == "signupEvent") parts.getOrNull(2) else parts.getOrNull(1)
        return screen to id
    }

    /** Why a queued push is wrong before it's even tapped: onMessageCreate already pushes every chat message. */
    fun duplicate(notification: Map<String, Any?>): String? =
        if (notification["chatId"] == null) null
        else "queues its own push for a chat message (\"${notification["title"]}\", type ${notification["type"]}), but the " +
            "onMessageCreate Cloud Function already pushes every message, so the recipient is notified twice"

    /** Why tapping [notification] (a pendingNotifications doc, ids concrete) on Android goes wrong, or null if it's fine. */
    fun problem(notification: Map<String, Any?>, recipient: String): String? {
        val type = notification["type"] as? String
            ?: return "a push with no type (\"${notification["title"]}\"): Android can't tell what it is (it lands in the Bookings notification channel)"
        val route = routes.firstOrNull { it.type == type }
            ?: return "push type '$type' (\"${notification["title"]}\") isn't in the routing contract"
        val received = CloudFunction.dataFor(notification, recipient)
        val opened = androidScreen(received) ?: return "$type opens nothing on Android; the phone receives $received"
        fun expectedId(template: String) =
            route.data.entries.firstOrNull { it.value == template }?.key?.let { notification[it] as? String } ?: template
        val ok = route.accept.any { (screen, id) -> screen == opened.first && (id == null || opened.second == expectedId(id)) }
        if (ok) return null
        val wanted = route.accept.joinToString(" or ") { (s, id) -> if (id == null) s else "$s(${expectedId(id)})" }
        return "$type opens ${opened.first}${opened.second?.let { "($it)" } ?: ""} on Android instead of $wanted; the phone receives $received"
    }
}
