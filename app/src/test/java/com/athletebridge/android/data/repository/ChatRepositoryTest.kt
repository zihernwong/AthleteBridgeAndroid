package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.ChatItem
import com.athletebridge.android.testutil.FakeFirestore
import com.google.firebase.Timestamp
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ChatRepositoryTest {

    private val fake = FakeFirestore()
    private val repo = ChatRepository(fake.instance, mockk(relaxed = true))

    private val earlier = Timestamp(Date(1_000_000))
    private val later = Timestamp(Date(2_000_000))

    /** observeChatsForUser merges three listeners; the third emission has all of them. */
    private suspend fun chatsFor(uid: String): List<ChatItem> = repo.observeChatsForUser(uid).take(3).last()

    private suspend fun chat(uid: String, id: String = "amy_zed") = chatsFor(uid).single { it.id == id }

    @Test
    fun `chat ids are deterministic and the doc is only created once`() = runBlocking {
        assertEquals("amy_zed", repo.createOrGetChat("zed", "amy"))
        assertEquals("amy_zed", repo.createOrGetChat("amy", "zed"))
        assertEquals(1, fake.writes.count { it.path == "chats/amy_zed" })
        assertEquals(listOf("zed", "amy"), fake.doc("chats/amy_zed")!!["participants"])
    }

    @Test
    fun `a newer message from the other person is unread`() = runBlocking {
        fake.seed(
            "chats/amy_zed", mapOf(
                "participants" to listOf("amy", "zed"), "lastMessageText" to "see you at 7",
                "lastMessageAt" to later, "lastSenderId" to "zed", "readBy" to mapOf("amy" to earlier)
            )
        )
        assertEquals(1, chat("amy").unreadCount)
        assertEquals("see you at 7", chat("amy").lastMessage)
    }

    @Test
    fun `your own last message and already-read chats are not unread`() = runBlocking {
        fake.seed(
            "chats/amy_zed", mapOf(
                "participants" to listOf("amy", "zed"), "lastMessageAt" to later,
                "lastSenderId" to "amy", "readBy" to mapOf("amy" to later, "zed" to earlier)
            )
        )
        assertEquals(0, chat("amy").unreadCount)   // amy sent it
        assertEquals(1, chat("zed").unreadCount)   // zed hasn't read it
        fake.seed(
            "chats/amy_zed", mapOf(
                "participants" to listOf("amy", "zed"), "lastMessageAt" to earlier,
                "lastSenderId" to "zed", "readBy" to mapOf("amy" to later)
            )
        )
        assertEquals(0, chat("amy").unreadCount)
    }

    @Test
    fun `a new conversation started from the iOS app shows as unread`() = runBlocking {
        // iOS writes participantRefs, lastMessageText and lastMessageAt — it never writes lastSenderId.
        fake.seed(
            "chats/amy_zed", mapOf(
                "participantRefs" to listOf(fake.ref("coaches/zed"), fake.ref("clients/amy")),
                "lastMessageText" to "Hi! Are you taking new students?",
                "lastMessageAt" to later
            )
        )
        assertEquals(1, chat("amy").unreadCount)
    }

    @Test
    fun `a reply sent from iOS after your Android message shows as unread`() = runBlocking {
        // amy (Android) sent at `earlier`: lastSenderId=amy, readBy.amy=earlier.
        // zed replied from iOS at `later`: iOS bumps lastMessageText/lastMessageAt only.
        fake.seed(
            "chats/amy_zed", mapOf(
                "participants" to listOf("amy", "zed"),
                "lastMessageText" to "Sounds good, see you then",
                "lastMessageAt" to later,
                "lastSenderId" to "amy",
                "readBy" to mapOf("amy" to earlier)
            )
        )
        assertEquals(1, chat("amy").unreadCount)
    }

    @Test
    fun `a never-opened chat whose last message you sent is not unread, nor is an empty new chat`() = runBlocking {
        fake.seed("chats/amy_zed", mapOf("participants" to listOf("amy", "zed"), "lastMessageAt" to later, "lastSenderId" to "amy"))
        assertEquals(0, chat("amy").unreadCount)
        repo.createOrGetChat("bob", "cat")
        assertEquals(0, chat("cat", id = "bob_cat").unreadCount)
    }

    @Test
    fun `iOS chats found through participantRefs list both participants once`() = runBlocking {
        fake.seed(
            "chats/amy_zed", mapOf(
                "participants" to listOf("amy", "zed"),
                "participantRefs" to listOf(fake.ref("coaches/amy"), fake.ref("coaches/zed")),
                "lastMessageAt" to later
            )
        )
        val chats = chatsFor("amy")
        assertEquals(1, chats.size)
        assertEquals(listOf("amy", "zed"), chats.single().participantIds)
    }

    @Test
    fun `chats are listed newest first`() = runBlocking {
        fake.seed("chats/a_old", mapOf("participants" to listOf("amy", "old"), "lastMessageAt" to earlier))
        fake.seed("chats/a_new", mapOf("participants" to listOf("amy", "new"), "lastMessageAt" to later))
        assertEquals(listOf("a_new", "a_old"), chatsFor("amy").map { it.id })
    }

    // ── Messages ────────────────────────────────────────────────────────────

    @Test
    fun `messages are sorted oldest first using createdAt or the legacy timestamp field`() = runBlocking {
        fake.seed("chats/c/messages/m1", mapOf("senderId" to "amy", "text" to "second", "createdAt" to later))
        fake.seed("chats/c/messages/m2", mapOf("senderId" to "zed", "text" to "first", "timestamp" to earlier))
        val messages = repo.observeMessages("c").first()
        assertEquals(listOf("first", "second"), messages.map { it.text })
    }

    @Test
    fun `a just-sent message whose server time is still pending appears at the bottom`() = runBlocking {
        // Locally, before the server acknowledges a write, Firestore reports serverTimestamp() fields as null.
        // Offline, that state lasts until the phone reconnects.
        fake.seed("chats/c/messages/old", mapOf("senderId" to "zed", "text" to "earlier message", "createdAt" to earlier))
        fake.seed("chats/c/messages/new", mapOf("senderId" to "amy", "text" to "just sent"))
        val messages = repo.observeMessages("c").first()
        assertEquals("just sent", messages.last().text)
    }

    @Test
    fun `sending a message updates the chat preview and marks it read for the sender`() = runBlocking {
        val chatId = repo.createOrGetChat("amy", "zed")
        repo.sendMessage(chatId, "amy", "On my way", "Amy").getOrThrow()
        val msg = fake.docsIn("chats/$chatId/messages").values.single()
        assertEquals("On my way", msg["text"])
        assertEquals("amy", msg["senderId"])
        val summary = fake.doc("chats/$chatId")!!
        assertEquals("On my way", summary["lastMessageText"])
        assertEquals("amy", summary["lastSenderId"])
        assertEquals(fake.serverTime, (summary["readBy"] as Map<*, *>)["amy"])
        assertEquals(0, chat("amy").unreadCount)
        assertTrue(chat("zed").unreadCount > 0)
    }
}
