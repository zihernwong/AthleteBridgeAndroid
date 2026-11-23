package com.athletebridge.android.testutil

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Sanity checks so a failing repository test points at the app, not at the fake. */
class FakeFirestoreTest {

    private val fake = FakeFirestore()
    private val db = fake.instance

    @Test
    fun `set, merge, dotted update, sentinels`() = runBlocking {
        val ref = db.collection("events").document("e1")
        ref.set(mapOf("count" to 1L, "nested" to mapOf("a" to 1L), "tags" to listOf("x"))).await()
        ref.update(
            mapOf(
                "count" to FieldValue.increment(2),
                "nested.b" to "two",
                "tags" to FieldValue.arrayUnion("y", "x"),
                "when" to FieldValue.serverTimestamp()
            )
        ).await()
        ref.update("nested.a", FieldValue.delete()).await()
        ref.set(mapOf("extra" to true), SetOptions.merge()).await()

        val doc = fake.doc("events/e1")!!
        assertEquals(3L, doc["count"])
        assertEquals(mapOf("b" to "two"), doc["nested"])
        assertEquals(listOf("x", "y"), doc["tags"])
        assertEquals(fake.serverTime, doc["when"])
        assertEquals(true, doc["extra"])
    }

    @Test
    fun `update on a missing document fails and a failed batch changes nothing`() = runBlocking {
        fake.seed("a/1", mapOf("v" to 1L))
        val batch = db.batch()
        batch.update(db.collection("a").document("1"), mapOf("v" to 2L))
        batch.update(db.collection("a").document("missing"), mapOf("v" to 2L))
        try {
            batch.commit().await()
            fail("expected NOT_FOUND")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.NOT_FOUND, e.code)
        }
        assertEquals(1L, fake.doc("a/1")!!["v"])
    }

    @Test
    fun `empty document ids are rejected like the real SDK`() {
        try {
            db.collection("coaches").document("")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `queries filter, order and skip docs missing the ordered field`() = runBlocking {
        fake.seed("r/1", mapOf("coachId" to "c", "n" to 2L))
        fake.seed("r/2", mapOf("coachId" to "c", "n" to 1L))
        fake.seed("r/3", mapOf("coachId" to "c"))
        fake.seed("r/4", mapOf("coachId" to "other", "n" to 0L))
        val ids = db.collection("r").whereEqualTo("coachId", "c").orderBy("n").get().await()
            .documents.map { it.id }
        assertEquals(listOf("2", "1"), ids)
    }

    @Test
    fun `transaction applies writes only when the function succeeds`() = runBlocking {
        fake.seed("t/1", mapOf("v" to 1L))
        val ref = db.collection("t").document("1")
        try {
            db.runTransaction<Unit> { txn ->
                txn.update(ref, mapOf("v" to 5L))
                throw IllegalStateException("nope")
            }.await()
            fail("expected failure")
        } catch (e: IllegalStateException) {
            assertEquals("nope", e.message)
        }
        assertEquals(1L, fake.doc("t/1")!!["v"])
        db.runTransaction { txn -> txn.update(ref, mapOf("v" to 7L)) }.await()
        assertEquals(7L, fake.doc("t/1")!!["v"])
    }

    @Test
    fun `add generates ids and snapshots expose data`() = runBlocking {
        val added = db.collection("x").add(mapOf("k" to "v")).await()
        val snap = db.collection("x").document(added.id).get().await()
        assertTrue(snap.exists())
        assertEquals("v", snap.data!!["k"])
        assertNull(db.collection("x").document("nope").get().await().data)
    }
}
