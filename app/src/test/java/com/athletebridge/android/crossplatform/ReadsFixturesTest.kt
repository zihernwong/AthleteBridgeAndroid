package com.athletebridge.android.crossplatform

import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.testutil.MainDispatcherRule
import com.athletebridge.android.testutil.withDefaults
import com.athletebridge.android.util.PaymentLinks
import com.athletebridge.android.util.ReminderScheduler
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

/** The real cross-platform check: Android reading and acting on what the iOS app wrote. */
class ReadsIosFixturesTest : ReadsFixtures("ios")

/** Self-check: the same tests on Android's own recordings. A failure here is a test bug or an Android-only bug. */
class ReadsAndroidFixturesTest : ReadsFixtures("android")

/** Collects every problem in a test so one run reports all the incompatibilities in a story. */
class Problems(private val what: String) {
    private val list = mutableListOf<String>()
    fun add(msg: String) { list += msg }
    fun eq(label: String, expected: Any?, actual: Any?) { if (expected != actual) list += "$label: expected $expected, got $actual" }
    fun check(ok: Boolean, msg: () -> String) { if (!ok) list += msg() }
    fun assertNone() { if (list.isNotEmpty()) fail("$what:\n  " + list.distinct().joinToString("\n  ")) }
}

/**
 * Loads another app's recorded stories (cross-platform/fixtures/<writer>) into the in-memory Firestore and runs
 * the Android app's real readers and actions on them: every screen must find each document, show the values the
 * other app saved, act on it so every copy updates, and route its pushes.
 */
abstract class ReadsFixtures(private val writer: String) {

    @get:Rule val main = MainDispatcherRule()

    @Before fun stubAlarms() {
        mockkObject(ReminderScheduler)
        every { ReminderScheduler.scheduleReminder(any(), any(), any()) } just Runs
    }

    @After fun tearDown() = unmockkAll()

    private val writerName = if (writer == "ios") "iOS" else "Android"

    private fun fixture(story: String): Fixture {
        val f = Fixture.load(writer, story)
        assumeTrue("No $writer recording of '$story' yet (cross-platform/check.sh --record makes one)", f != null)
        return f!!
    }

    /** A fresh Android world holding [f]'s documents as they stood at [checkpoint]. */
    private fun world(f: Fixture, checkpoint: String): Pair<AndroidWorld, Seeder> {
        val w = AndroidWorld(AndroidWorld.READING_UIDS)
        val seed = Seeder(w.fake, w.uids)
        seed.seed(f, checkpoint)
        return w to seed
    }

    private fun <T> io(block: suspend () -> T): T = runBlocking { block() }

    /** Expected values at [checkpoint]: the story's own, plus each recorded checkpoint's expectations so far (status only from [checkpoint] itself). */
    private fun expected(story: Story, f: Fixture, checkpoint: String): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        for (cp in story.checkpoints.take(story.checkpoints.indexOf(checkpoint) + 1).filter(f::has)) {
            story.expect(cp).forEach { (k, v) -> if (k != "status" || cp == checkpoint) out[k as String] = v }
        }
        return out
    }

    private val knownStatuses = ((Contract.json["statuses"] as Map<*, *>)["known"] as List<*>).toSet()

    private fun date(iso: Any?) = Date.from(java.time.Instant.parse(iso as String))

    private fun copies(w: AndroidWorld, id: String, coaches: List<String>, clients: List<String>) =
        listOf("bookings/$id") + coaches.map { "coaches/${w.uid(it)}/bookings/$id" } + clients.map { "clients/${w.uid(it)}/bookings/$id" }

    // ── 1:1 bookings ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bookings show the same session on every Android screen`() {
        val f = fixture("booking")
        val s = Story("booking")
        val p = Problems("Android reading $writerName's booking story")
        for (cp in s.checkpoints.drop(1).filter(f::has)) {
            val (w, seed) = world(f, cp)
            val id = seed.id("booking")
            val want = expected(s, f, cp)
            val start = date(want["start"] ?: s.str("start"))
            val end = date(want["end"] ?: s.str("end"))
            val views: Map<String, BookingItem?> = mapOf(
                "coach's bookings" to io { w.bookings.observeCoachBookings(w.uid("coach")).first() }.firstOrNull { it.id == id },
                "client's bookings" to io { w.bookings.observeClientBookings(w.uid("client")).first() }.firstOrNull { it.id == id },
                "booking deep link (bookings/{id})" to io { w.bookings.fetchBookingAny(id, "someone-else", "CLIENT") },
                "coach's booking history" to io { w.bookings.fetchBookingsPage(w.uid("coach"), "COACH", null).first }.firstOrNull { it.id == id },
                "coach's calendar day" to io {
                    w.bookings.fetchBookingsForCoach(w.uid("coach"), Date(start.time - 12 * 3_600_000L), Date(start.time + 12 * 3_600_000L))
                }.firstOrNull { it.id == id }
            )
            for ((where, b) in views) {
                if (b == null) { p.add("$cp: missing from the $where"); continue }
                val at = "$cp, $where"
                p.eq("$at: coach", w.uid("coach"), b.coachID)
                p.eq("$at: client", w.uid("client"), b.clientID)
                p.eq("$at: coach name", Actors.name("coach"), b.coachName)
                p.eq("$at: client name", Actors.name("client"), b.clientName)
                p.eq("$at: start", start, b.startAt)
                p.eq("$at: end", end, b.endAt)
                p.eq("$at: location", s.str("location"), b.location)
                p.eq("$at: notes", s.str("notes"), b.notes)
                p.check(b.status in knownStatuses) { "$at: status '${b.status}' is not one Android knows" }
                want["status"]?.let { p.eq("$at: status", it, b.status) }
                want["rateUSD"]?.let { p.eq("$at: rate", (it as Number).toDouble(), b.rateUSD) }
                want["coachNote"]?.let { p.eq("$at: coach note", it, b.coachNote) }
                want["paymentStatus"]?.let { p.eq("$at: payment status", it, b.paymentStatus) }
                want["sessionRecap"]?.let { p.eq("$at: session recap", it, b.sessionRecap) }
            }
        }
        p.assertNone()
    }

    @Test
    fun `an Android coach can accept a booking requested on the other app`() {
        val f = fixture("booking")
        val s = Story("booking")
        val (w, seed) = world(f, "requested")
        val id = seed.id("booking")
        val p = Problems("Android coach accepting $writerName's booking request")
        w.acceptBooking("coach").run {
            loadBooking(id)
            p.check(uiState.value.booking != null) { "the accept screen can't load the booking" }
            acceptBooking(id, s.dbl("rateUSD"), s.str("coachNote"))
            uiState.value.error?.let { p.add("accepting failed: $it") }
        }
        for (path in copies(w, id, listOf("coach"), listOf("client"))) {
            val d = w.fake.doc(path)
            p.eq("$path Status", "Pending Acceptance", d?.get("Status"))
            p.eq("$path status", "Pending Acceptance", d?.get("status"))
            p.eq("$path RateUSD", s.dbl("rateUSD"), d?.get("RateUSD"))
        }
        val push = w.pushes("client").firstOrNull { it["type"] == "booking_accepted" }
        p.eq("push to the client", id, push?.get("bookingId"))
        p.assertNone()
    }

    @Test
    fun `an Android client can confirm, and later cancel, a booking from the other app`() {
        val f = fixture("booking")
        val from = if (f.has("accepted")) "accepted" else "requested"
        val (w, seed) = world(f, from)
        val id = seed.id("booking")
        val p = Problems("Android client confirming then cancelling $writerName's booking (from '$from')")
        w.reviewBooking("client").run {
            loadBooking(id)
            p.check(uiState.value.booking != null) { "the review screen can't load the booking" }
            confirmBooking(id)
            uiState.value.error?.let { p.add("confirming failed: $it") }
        }
        val paths = copies(w, id, listOf("coach"), listOf("client"))
        for (path in paths) p.eq("$path status after confirming", "confirmed", w.fake.doc(path)?.get("Status"))
        val booking = io { w.bookings.fetchClientBooking(id, w.uid("client")) }
        if (booking == null) p.add("the client's copy no longer parses") else w.clientConfirmed("client").run {
            cancelBooking(booking)
            uiState.value.error?.let { p.add("cancelling failed: $it") }
        }
        for (path in paths) {
            p.eq("$path Status after cancelling", "cancelled", w.fake.doc(path)?.get("Status"))
            p.eq("$path status after cancelling", "cancelled", w.fake.doc(path)?.get("status"))
        }
        p.check(w.pushes("coach").any { it["type"] == "booking_cancelled" && it["bookingId"] == id }) { "the coach isn't told about the cancellation" }
        p.assertNone()
    }

    @Test
    fun `an Android coach can reschedule a confirmed booking from the other app`() {
        val f = fixture("booking")
        val s = Story("booking")
        val (w, seed) = world(f, "confirmed")
        val id = seed.id("booking")
        val p = Problems("Android coach rescheduling $writerName's confirmed booking")
        val booking = io { w.bookings.fetchBooking(id, w.uid("coach")) }
        if (booking == null) { p.add("the coach's copy doesn't parse"); p.assertNone(); return }
        w.coachConfirmed("coach").run {
            rescheduleBooking(booking, s.date("newStart"), s.date("newEnd"))
            uiState.value.error?.let { p.add("rescheduling failed: $it") }
        }
        for (path in copies(w, id, listOf("coach"), listOf("client"))) {
            val d = w.fake.doc(path)
            for (key in listOf("StartAt", "startAt")) p.eq("$path $key", s.date("newStart"), (d?.get(key) as? com.google.firebase.Timestamp)?.toDate())
        }
        p.assertNone()
    }

    // ── Group sessions ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `group sessions show every participant the same session`() {
        val f = fixture("group_booking")
        val s = Story("group_booking")
        val p = Problems("Android reading $writerName's group session story")
        val coaches = listOf("coach", "coach2")
        val clients = listOf("client", "client2")
        for (cp in s.checkpoints.drop(1).filter(f::has)) {
            val (w, seed) = world(f, cp)
            val id = seed.id("booking")
            val want = expected(s, f, cp)
            val accepted = (want["accepted"] as? List<*>).orEmpty()
            val confirmed = (want["confirmed"] as? List<*>).orEmpty()
            val views = coaches.associate { "$it's bookings" to io { w.bookings.observeCoachBookings(w.uid(it)).first() }.firstOrNull { b -> b.id == id } } +
                clients.associate { "$it's bookings" to io { w.bookings.observeClientBookings(w.uid(it)).first() }.firstOrNull { b -> b.id == id } }
            for ((where, b) in views) {
                if (b == null) { p.add("$cp: missing from $where"); continue }
                val at = "$cp, $where"
                p.eq("$at: coaches", coaches.map(w::uid), b.allCoachIDs)
                p.eq("$at: clients", clients.map(w::uid), b.allClientIDs)
                p.eq("$at: coach names", coaches.map(Actors::name), b.allCoachNames)
                p.eq("$at: client names", clients.map(Actors::name), b.allClientNames)
                p.eq("$at: start", s.date("start"), b.startAt)
                p.eq("$at: location", s.str("location"), b.location)
                p.eq("$at: notes", s.str("notes"), b.notes)
                p.check(b.status in knownStatuses) { "$at: status '${b.status}' is not one Android knows" }
                want["status"]?.let { p.eq("$at: status", it, b.status) }
                for (c in coaches) p.eq("$at: $c accepted", c in accepted, b.coachAcceptances?.get(w.uid(c)) == true)
                for (c in clients) p.eq("$at: $c confirmed", c in confirmed, b.clientConfirmations?.get(w.uid(c)) == true)
                if ("coach" in accepted) p.eq("$at: coach's rate", s.dbl("coachRate"), b.coachRates?.get(w.uid("coach")))
                if ("coach2" in accepted) p.eq("$at: coach2's rate", s.dbl("coach2Rate"), b.coachRates?.get(w.uid("coach2")))
            }
        }
        p.assertNone()
    }

    @Test
    fun `Android participants can accept, confirm and cancel a group session from the other app`() {
        val f = fixture("group_booking")
        val s = Story("group_booking")
        val (w, seed) = world(f, "requested")
        val id = seed.id("booking")
        val p = Problems("Android participants acting on $writerName's group session")
        val paths = copies(w, id, listOf("coach", "coach2"), listOf("client", "client2"))
        w.acceptBooking("coach").run { loadBooking(id); acceptBooking(id, s.dbl("coachRate"), null); uiState.value.error?.let { p.add("coach accept failed: $it") } }
        for (path in paths) {
            val d = w.fake.doc(path)
            p.eq("$path coachAcceptances.coach", true, (d?.get("coachAcceptances") as? Map<*, *>)?.get(w.uid("coach")))
            p.eq("$path CoachAcceptances.coach", true, (d?.get("CoachAcceptances") as? Map<*, *>)?.get(w.uid("coach")))
        }
        w.acceptBooking("coach2").run { loadBooking(id); acceptBooking(id, s.dbl("coach2Rate"), null); uiState.value.error?.let { p.add("coach2 accept failed: $it") } }
        for (path in paths) p.eq("$path status once both coaches accept", "Pending Acceptance", w.fake.doc(path)?.get("Status"))
        for (client in listOf("client", "client2")) w.reviewBooking(client).run {
            loadBooking(id); confirmBooking(id); uiState.value.error?.let { p.add("$client confirm failed: $it") }
        }
        for (path in paths) p.eq("$path status once both clients confirm", "confirmed", w.fake.doc(path)?.get("Status"))
        val booking = io { w.bookings.fetchBooking(id, w.uid("coach2")) }
        if (booking != null) w.coachConfirmed("coach2").run { cancelBooking(booking); uiState.value.error?.let { p.add("cancel failed: $it") } }
        for (path in paths) p.eq("$path status after coach2 cancels", "cancelled", w.fake.doc(path)?.get("status"))
        p.assertNone()
    }

    // ── Chat ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chats and messages show up on Android`() {
        val f = fixture("chat")
        val s = Story("chat")
        val p = Problems("Android reading $writerName's chat story")
        for (cp in s.checkpoints.drop(1).filter(f::has)) {
            val (w, seed) = world(f, cp)
            val chatId = seed.id("chat")
            for (actor in listOf("coach", "client")) {
                // The list merges three listeners; the third emission has all of them
                val chat = io { w.chats.observeChatsForUser(w.uid(actor)).take(3).toList().last() }.firstOrNull { it.id == chatId }
                if (chat == null) { p.add("$cp: the chat is missing from the $actor's chat list"); continue }
                p.check(chat.participantIds.containsAll(listOf(w.uid("coach"), w.uid("client")))) { "$cp: $actor's chat lists participants ${chat.participantIds}" }
                val last = when (cp) { "messaged" -> s.str("message"); "replied" -> s.str("reply"); else -> null }
                p.eq("$cp: $actor's chat preview", last, chat.lastMessage)
                val sender = when (cp) { "messaged" -> "client"; "replied" -> "coach"; else -> null }
                if (sender != null) p.eq("$cp: $actor sees the chat as unread", actor != sender, chat.unreadCount > 0)
            }
            val messages = io { w.chats.observeMessages(chatId).first() }
            val texts = messages.map { it.text }
            val want = when (cp) { "messaged" -> listOf(s.str("message")); "replied" -> listOf(s.str("message"), s.str("reply")); else -> emptyList() }
            p.eq("$cp: messages in order", want, texts)
            p.check(messages.all { it.timestamp != null }) { "$cp: some messages have no time, so they sort wrongly" }
            if (want.isNotEmpty()) p.eq("$cp: first message sender", w.uid("client"), messages.firstOrNull()?.senderId)
        }
        p.assertNone()
    }

    @Test
    fun `an Android coach can reply in a chat the other app started`() {
        val f = fixture("chat")
        val s = Story("chat")
        val (w, seed) = world(f, "messaged")
        val chatId = seed.id("chat")
        val p = Problems("Android coach replying in $writerName's chat")
        p.eq("Android opens the same chat id", chatId, io { w.chats.createOrGetChat(w.uid("coach"), w.uid("client")) })
        io { w.chats.sendMessage(chatId, w.uid("coach"), s.str("reply"), Actors.name("coach")) }.exceptionOrNull()?.let { p.add("sending failed: $it") }
        val chat = w.fake.doc("chats/$chatId")
        p.eq("chat preview", s.str("reply"), chat?.get("lastMessageText"))
        p.eq("last sender", w.uid("coach"), chat?.get("lastSenderId"))
        p.eq("messages", listOf(s.str("message"), s.str("reply")), io { w.chats.observeMessages(chatId).first() }.map { it.text })
        p.assertNone()
    }

    // ── Clubs ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `clubs, members and announcements show up on Android`() {
        val f = fixture("club")
        val s = Story("club")
        val p = Problems("Android reading $writerName's club story")
        val order = s.checkpoints
        fun reached(cp: String, step: String) = f.has(step) && order.indexOf(cp) >= order.indexOf(step)
        for (cp in order.drop(1).filter(f::has)) {
            val (w, seed) = world(f, cp)
            val clubId = seed.id("club")
            val club = io { w.places.fetchPlaces() }.firstOrNull { it.id == clubId }
            if (club == null) { p.add("$cp: the club is missing from Places to Play"); continue }
            p.eq("$cp: name", s.str("name"), club.name)
            p.eq("$cp: address", s.str("address"), club.address)
            p.eq("$cp: schedule", s.stringMap("playingTimes"), club.weeklySchedule)
            p.eq("$cp: price", s.str("price"), club.pricing)
            if (reached(cp, "claimed")) {
                p.check(w.uid("coach") in club.adminIds) { "$cp: the coach isn't an admin (admins ${club.adminIds})" }
                p.eq("$cp: contact", w.uid("coach"), club.contactUserId)
            }
            val clientId = w.uid("client")
            when {
                reached(cp, "approved") -> {
                    p.eq("$cp: member name", Actors.name("client"), club.members.firstOrNull { it.id == clientId }?.name)
                    p.check(clientId !in club.pendingMembers) { "$cp: the client is still pending" }
                }
                reached(cp, "join_requested") ->
                    p.eq("$cp: pending member name", Actors.name("client"), club.pendingMemberEntries.firstOrNull { it.id == clientId }?.name)
            }
            if (reached(cp, "announced")) {
                val news = io { w.places.fetchAnnouncements(clubId) }
                p.eq("$cp: announcement", listOf(s.str("announcement")), news.map { it.body })
                p.eq("$cp: announcement author", w.uid("coach"), news.firstOrNull()?.createdBy)
            }
        }
        p.assertNone()
    }

    @Test
    fun `an Android member can join a club listed on the other app`() {
        val f = fixture("club")
        val from = if (f.has("claimed")) "claimed" else "created"
        val (w, seed) = world(f, from)
        val clubId = seed.id("club")
        val p = Problems("Android joining $writerName's club")
        io { w.places.requestMembership(clubId, w.uid("client"), Actors.name("client")) }.exceptionOrNull()?.let { p.add("join request failed: $it") }
        w.clubManagement("coach").run { load(); approveMember(clubId, w.uid("client")) }
        val club = io { w.places.fetchPlace(clubId) }
        p.eq("member name", Actors.name("client"), club?.members?.firstOrNull { it.id == w.uid("client") }?.name)
        p.check(w.pushes("client").any { it["type"] == "club_approved" && it["placeId"] == clubId }) { "the new member isn't told" }
        p.assertNone()
    }

    // ── Signup events ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `signup events, spots and the waitlist show up on Android`() = withDefaults(Story("signup_event").str("timeZone")) {
        val f = fixture("signup_event")
        val s = Story("signup_event")
        val p = Problems("Android reading $writerName's signup event story")
        for (cp in s.checkpoints.drop(1).filter(f::has)) {
            val (w, seed) = world(f, cp)
            val events = io { w.places.fetchEvents(seed.id("club")) }
            val e = events.firstOrNull { it.id == seed.id("event") }
            if (e == null) { p.add("$cp: the event is missing from the club's events (${events.map { it.id }})"); continue }
            p.eq("$cp: title", s.str("title"), e.title)
            p.eq("$cp: description", s.str("description"), e.description)
            p.eq("$cp: date", s.date("eventDate"), e.eventDate)
            p.eq("$cp: location", s.str("location"), e.location)
            p.eq("$cp: spots", s.int("maxSignups"), e.maxSignups)
            p.eq("$cp: fee", s.dbl("feeUSD"), e.feeUSD)
            p.eq("$cp: payment link", s.str("paymentLink"), e.paymentLink)
            p.eq("$cp: repeats weekly", true, e.isRecurringWeekly)
            val signedUp = when (cp) { "signed_up", "waitlisted" -> listOf("client"); "promoted", "next_week" -> listOf("client2"); else -> emptyList() }
            p.eq("$cp: signed up", signedUp.map(w::uid), e.signups.map { it.userId })
            p.eq("$cp: signup names", signedUp.map(Actors::name), e.signups.map { it.name })
            p.eq("$cp: signup count", signedUp.size, e.signupCount)
            p.eq("$cp: waitlist", if (cp == "waitlisted") listOf(w.uid("client2")) else emptyList<String>(), e.waitlist.map { it.userId })
            if (cp == "next_week") {
                val next = events.firstOrNull { it.id == seed.id("nextEvent") }
                p.eq("next week's date (same local time across the DST change)", s.date("nextEventDate"), next?.eventDate)
                p.eq("next week's signups", 0, next?.signupCount)
                p.eq("next week's fee", s.dbl("feeUSD"), next?.feeUSD)
            }
        }
        p.assertNone()
    }

    @Test
    fun `Android players can sign up for, wait for and leave an event from the other app`() = withDefaults(Story("signup_event").str("timeZone")) {
        val f = fixture("signup_event")
        val s = Story("signup_event")
        val (w, seed) = world(f, "created")
        val eventId = seed.id("event")
        val p = Problems("Android players using $writerName's signup event")
        io { w.places.registerForEvent(eventId, w.uid("client"), Actors.name("client"), Actors.email("client")) }.exceptionOrNull()?.let { p.add("signing up failed: ${it.message}") }
        p.check(io { w.places.registerForEvent(eventId, w.uid("client2"), Actors.name("client2"), Actors.email("client2")) }.isFailure) { "a full event let a second player in" }
        io { w.places.joinWaitlist(eventId, w.uid("client2"), Actors.name("client2"), Actors.email("client2")) }.exceptionOrNull()?.let { p.add("joining the waitlist failed: $it") }
        io { w.places.unregisterFromEvent(eventId, w.uid("client")) }.exceptionOrNull()?.let { p.add("leaving failed: ${it.message}") }
        val e = io { w.places.fetchAllEvents() }.firstOrNull { it.id == eventId }
        p.eq("signed up after the first player leaves", listOf(w.uid("client2")), e?.signups?.map { it.userId })
        p.check(w.pushes("client2").any { it["type"] == "event_waitlist_promoted" }) { "the promoted player isn't told" }
        if (e != null) {
            io { w.places.createNextOccurrence(e) }.exceptionOrNull()?.let { p.add("rolling to next week failed: ${it.message}") }
            val next = io { w.places.fetchAllEvents() }.firstOrNull { it.id != eventId }
            p.eq("next week's date from Android (same local time across DST)", s.date("nextEventDate"), next?.eventDate)
        }
        p.assertNone()
    }

    // ── Stringing ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `stringer profiles and orders show up on Android`() {
        val f = fixture("stringing")
        val s = Story("stringing")
        val p = Problems("Android reading $writerName's stringing story")
        for (cp in s.checkpoints.drop(1).filter(f::has)) {
            val (w, seed) = world(f, cp)
            val stringerUid = w.uid(s.str("stringer"))
            // iOS keys a stringer profile by its owner's uid, Android by a generated id: find it by owner
            val profile = io { w.stringers.fetchStringers() }.firstOrNull { it.userId == stringerUid }
            if (profile == null) { p.add("$cp: the stringer is missing from the stringer list"); continue }
            p.eq("$cp: stringer account", stringerUid, profile.userId)
            p.eq("$cp: stringer name", s.str("name"), profile.name)
            p.eq("$cp: string price", s.dbl("stringPrice"), profile.strings[s.str("string")])
            p.eq("$cp: labor", s.dbl("laborCost"), profile.laborCost)
            p.eq("$cp: meetup", s.str("meetup"), profile.meetupLocation)
            p.eq("$cp: 'my stringer profile' lookup", profile.id, io { w.stringers.fetchStringerByUserId(stringerUid) }?.id)
            if (cp == "profile") continue
            val orderId = seed.id("order")
            val mine = io { w.stringers.fetchOrdersForBuyer(w.uid("client")) }.firstOrNull { it.id == orderId }
            val incoming = io { w.stringers.fetchOrdersForStringer(profile.id) }.firstOrNull { it.id == orderId }
            p.check(mine != null) { "$cp: the order is missing from the buyer's orders" }
            p.check(incoming != null) { "$cp: the order is missing from the stringer's incoming orders" }
            val o = mine ?: incoming ?: continue
            p.eq("$cp: racket", s.str("racket"), o.racketName)
            p.eq("$cp: string", s.str("string"), o.selectedString)
            p.eq("$cp: tension", s.int("tension"), o.tension)
            p.eq("$cp: total", s.dbl("stringPrice") + s.dbl("laborCost"), o.total)
            p.eq("$cp: string cost", s.dbl("stringPrice"), o.stringCost)
            p.eq("$cp: labor cost", s.dbl("laborCost"), o.laborCost)
            p.eq("$cp: buyer", w.uid("client"), o.buyerId)
            p.eq("$cp: buyer name", Actors.name("client"), o.buyerName)
            p.eq("$cp: status", if (cp == "ordered") "placed" else cp, o.status)
            if (cp != "ordered") p.check(o.statusHistory.containsKey(cp)) { "$cp: no time for this stage in the order timeline (${o.statusHistory.keys})" }
        }
        p.assertNone()
    }

    @Test
    fun `an Android stringer can work an order placed on the other app`() {
        val f = fixture("stringing")
        val s = Story("stringing")
        val (w, seed) = world(f, "ordered")
        val orderId = seed.id("order")
        val p = Problems("Android stringer updating $writerName's order")
        val detail = w.stringerOrder(s.str("stringer")).apply { loadOrder(orderId) }
        p.check(detail.uiState.value.order != null) { "the order screen can't load the order" }
        detail.updateStatus("accepted")
        p.eq("status", "accepted", w.fake.doc("stringerOrders/$orderId")?.get("status"))
        val push = w.pushes("client").firstOrNull { it["type"] == "stringer_order" }
        p.eq("the buyer's push points at the order", orderId, push?.get("stringerOrderId"))
        p.assertNone()
    }

    // ── Reviews, tournaments, profiles ─────────────────────────────────────────────────────────

    @Test
    fun `reviews show up on Android`() {
        val f = fixture("review")
        val s = Story("review")
        val (w, seed) = world(f, "reviewed")
        val p = Problems("Android reading $writerName's review")
        val coach = w.uid("coach")
        val onProfile = io { w.reviews.fetchReviewsForCoach(coach) }.firstOrNull { it.id == seed.id("review") }
        if (onProfile == null) p.add("the review is missing from the coach's reviews") else {
            p.eq("rating", s.int("rating"), onProfile.rating)
            p.eq("comment", s.str("comment"), onProfile.comment)
            p.eq("reviewer", w.uid("client"), onProfile.reviewerId)
            p.eq("reviewer name", Actors.name("client"), onProfile.reviewerName)
        }
        p.eq("coach's rating summary", s.int("rating").toDouble() to 1, io { w.reviews.fetchRatingSummaries() }[coach]?.let { it.average to it.count })
        p.check(io { w.reviews.fetchReviewsByReviewer(w.uid("client")) }.any { it.id == seed.id("review") }) { "the review is missing from the client's own reviews" }
        p.assertNone()
    }

    @Test
    fun `tournaments and partner seekers show up on Android`() {
        val f = fixture("tournament")
        val s = Story("tournament")
        val p = Problems("Android reading $writerName's tournament story")
        for (cp in s.checkpoints.drop(1).filter(f::has)) {
            val (w, seed) = world(f, cp)
            val t = io { w.tournaments.fetchTournaments() }.firstOrNull { it.id == seed.id("tournament") }
            if (t == null) { p.add("$cp: the tournament is missing"); continue }
            p.eq("$cp: name", s.str("name"), t.name)
            p.eq("$cp: dates", s.date("start") to s.date("end"), t.startDate to t.endDate)
            p.eq("$cp: location", s.str("location"), t.location)
            p.eq("$cp: signup link", s.str("signupLink"), t.signupLink)
            val joined = when (cp) { "joined" -> listOf("client"); "partner_joined" -> listOf("client", "client2"); else -> emptyList() }
            p.eq("$cp: players", joined.map(w::uid).toSet(), t.participants.keys)
            for (a in joined) {
                val info = t.participants[w.uid(a)]
                val want = s.obj(a)
                p.eq("$cp: $a's details", Triple(want["gender"], want["events"], want["skillLevels"]), info?.let { Triple(it.gender, it.events, it.skillLevels) })
            }
        }
        p.assertNone()
    }

    @Test
    fun `a partner joining on Android tells a compatible player from the other app`() {
        val f = fixture("tournament")
        val s = Story("tournament")
        val (w, seed) = world(f, "joined")
        val p = Problems("Android player joining $writerName's tournament")
        val info = s.obj("client2")
        io {
            w.tournaments.joinTournament(seed.id("tournament"), w.uid("client2"), info["gender"] as String,
                (info["events"] as List<*>).map { it as String }, (info["skillLevels"] as List<*>).map { it as String }, Actors.name("client2"))
        }.exceptionOrNull()?.let { p.add("joining failed: $it") }
        p.check(w.pushes("client").any { it["type"] == "tournament_partner" }) { "the compatible player who joined on $writerName isn't told" }
        p.assertNone()
    }

    @Test
    fun `profiles and payment handles show up on Android`() {
        val f = fixture("profiles")
        val s = Story("profiles")
        val p = Problems("Android reading $writerName's profiles")
        val last = s.checkpoints.last { f.has(it) }
        val (w, _) = world(f, last)
        val coach: Coach? = io { w.coaches.fetchCoach(w.uid("coach")) }
        val c = s.obj("coach")
        if (coach == null) p.add("the coach profile doesn't load") else if (f.has("coach_profile")) {
            p.eq("coach name", Actors.name("coach"), coach.name)
            p.eq("specialties", c["specialties"], coach.specialties)
            p.eq("experience", (c["experienceYears"] as Number).toInt(), coach.experienceYears)
            p.eq("availability", c["availability"], coach.availability)
            p.eq("bio", c["bio"], coach.bio)
            p.eq("hourly rate", (c["hourlyRate"] as Number).toDouble(), coach.hourlyRate)
            p.eq("rate range", (c["rateRange"] as List<*>).map { (it as Number).toDouble() }, coach.rateRange)
            p.eq("meeting preference", c["meetingPreference"], coach.meetingPreference)
            p.eq("zip", c["zipCode"], coach.zipCode)
            p.eq("city", c["city"], coach.city)
            p.eq("tournament link", c["tournamentSoftwareLink"], coach.tournamentSoftwareLink)
            p.eq("cancellation window", (c["cancellationWindowHours"] as Number).toInt(), coach.cancellationWindowHours)
        }
        if (coach != null && f.has("payment_methods")) {
            for ((platform, handle) in s.stringMap("payments")) {
                val stored = coach.payments?.get(platform)
                p.check(stored != null) { "no $platform handle (payments: ${coach.payments})" }
                p.eq("$platform link", PaymentLinks.deepLink(platform, handle), stored?.let { PaymentLinks.deepLink(platform, it) })
            }
        }
        val client: Client? = io { w.clients.fetchClient(w.uid("client")) }
        val cl = s.obj("client")
        if (client == null) p.add("the client profile doesn't load") else if (f.has("client_profile")) {
            p.eq("client name", Actors.name("client"), client.name)
            p.eq("goals", cl["goals"], client.goals)
            p.eq("availability", cl["preferredAvailability"], client.preferredAvailability)
            p.eq("meeting preference", cl["meetingPreference"], client.meetingPreference)
            p.eq("skill level", cl["skillLevel"], client.skillLevel)
            p.eq("zip", cl["zipCode"], client.zipCode)
            p.eq("city", cl["city"], client.city)
            p.eq("bio", cl["bio"], client.bio)
        }
        p.assertNone()
    }

    @Test
    fun `saving a profile on Android keeps what the other app stored`() {
        val f = fixture("profiles")
        val last = Story("profiles").checkpoints.last { f.has(it) }
        val (w, _) = world(f, last)
        val p = Problems("Android saving profiles over $writerName's")
        for ((actor, path) in listOf("coach" to "coaches/${w.uid("coach")}", "client" to "clients/${w.uid("client")}")) {
            val before = w.fake.doc(path)?.keys.orEmpty()
            if (actor == "coach") io { w.coaches.fetchCoach(w.uid(actor))?.let { w.coaches.saveCoach(it) } }
            else io { w.clients.fetchClient(w.uid(actor))?.let { w.clients.saveClient(it) } }
            val after = w.fake.doc(path)?.keys.orEmpty()
            p.eq("$actor fields lost by saving on Android", emptySet<String>(), before - after)
        }
        p.assertNone()
    }

    // ── Pushes ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every push the other app sends opens the right Android screen`() {
        val p = Problems("Pushes $writerName queued, as Android handles them after the Cloud Function")
        var count = 0
        for (story in (Contract.json["stories"] as Map<*, *>).keys.map { it as String }) {
            val f = Fixture.load(writer, story) ?: continue
            val seed = Seeder(com.athletebridge.android.testutil.FakeFirestore(), AndroidWorld.READING_UIDS)
            for (cp in f.checkpoints) for ((recipient, doc) in f.pushes(cp.name)) {
                count++
                @Suppress("UNCHECKED_CAST")
                val notification = seed.tree(doc) as Map<String, Any?>
                (PushRoutes.duplicate(notification) ?: PushRoutes.problem(notification, seed.text(recipient)))
                    ?.let { p.add("$story/${cp.name}: $it") }
            }
        }
        assumeTrue("No $writer pushes recorded yet", count > 0)
        p.assertNone()
    }
}
