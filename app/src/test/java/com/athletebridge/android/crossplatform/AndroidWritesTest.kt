package com.athletebridge.android.crossplatform

import com.athletebridge.android.data.model.BadmintonStringer
import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.ReviewItem
import com.athletebridge.android.testutil.MainDispatcherRule
import com.athletebridge.android.testutil.withDefaults
import com.athletebridge.android.util.ReminderScheduler
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Runs each contract story through the Android app's real code (view models where a screen drives the step,
 * repositories otherwise) and records what lands in Firestore at every checkpoint. The iOS tests read and act on
 * these recordings (cross-platform/fixtures/android). A failure here means Android now writes something different
 * from what iOS was last checked against: re-run cross-platform/check.sh --record to re-record and re-check both apps.
 */
class AndroidWritesTest {

    @get:Rule val main = MainDispatcherRule()

    @Before fun stubAlarms() {
        mockkObject(ReminderScheduler)
        every { ReminderScheduler.scheduleReminder(any(), any(), any()) } just Runs
    }

    @After fun tearDown() = unmockkAll()

    private class Recording(val w: AndroidWorld, val r: Recorder, val s: Story) {
        fun uid(actor: String) = w.uid(actor)
        fun name(actor: String) = Actors.name(actor)
        fun checkpoint(name: String) = r.checkpoint(name)
        fun <T> io(block: suspend () -> T): T = runBlocking { block() }
    }

    private fun record(story: String, block: Recording.() -> Unit) {
        val w = AndroidWorld(AndroidWorld.RECORDING_UIDS, recording = true)
        val r = Recorder(story, w.fake)
        for (actor in Actors.keys) {
            r.register(w.uid(actor), actor)
            r.register(Actors.email(actor), "$actor-email")
        }
        Recording(w, r, Story(story)).block()
        r.finish()
    }

    @Test
    fun booking() = record("booking") {
        w.setUpActors("coach", "client")
        // Clients must book at one of the coach's preferred clubs. Android has no editor for these
        // (iOS's profile editor writes linkedPlaceIds), so the setup links the club directly.
        val club = io { w.places.createPlace(s.str("club"), "1 Court St, Minneapolis, MN", emptyMap(), null, uid("coach")).getOrThrow() }
        r.register(club, "club")
        io { w.fake.ref("coaches/${uid("coach")}").update("linkedPlaceIds", listOf(club)).await() }
        checkpoint("setup")

        w.bookingEditor("client").run {
            loadCoach(uid("coach"))
            createBooking(uid("coach"), s.date("start"), s.date("end"), s.str("location"), club, s.str("notes"))
            assertNull(uiState.value.error)
        }
        val id = w.fake.docsIn("clients/${uid("client")}/bookings").keys.single()
        r.register(id, "booking")
        checkpoint("requested")

        w.acceptBooking("coach").run {
            loadBooking(id)
            acceptBooking(id, s.dbl("rateUSD"), s.str("coachNote"))
            assertNull(uiState.value.error)
        }
        checkpoint("accepted")

        w.reviewBooking("client").run {
            loadBooking(id)
            confirmBooking(id)
            assertNull(uiState.value.error)
        }
        checkpoint("confirmed")

        val confirmed = io { w.bookings.fetchBooking(id, uid("coach"))!! }
        w.coachConfirmed("coach").run {
            acknowledgePayment(confirmed)
            assertNull(uiState.value.error)
        }
        checkpoint("paid")

        w.bookingDetail("coach").run {
            load(id, isCoach = true)
            saveSessionNotes(s.str("sessionNotes"))
            saveSessionRecap(s.str("recap"))
            assertNull(uiState.value.error)
        }
        checkpoint("session_notes")

        w.coachConfirmed("coach").run {
            rescheduleBooking(confirmed, s.date("newStart"), s.date("newEnd"))
            assertNull(uiState.value.error)
        }
        checkpoint("rescheduled")

        val current = io { w.bookings.fetchClientBooking(id, uid("client"))!! }
        w.clientConfirmed("client").run {
            cancelBooking(current)
            assertNull(uiState.value.error)
        }
        checkpoint("cancelled")
    }

    @Test
    fun group_booking() = record("group_booking") {
        w.setUpActors("coach", "coach2", "client", "client2")
        checkpoint("setup")

        // No Android screen creates group sessions yet (they're booked on iOS); this is the repository's shape for one.
        val id = io {
            w.bookings.createGroupBooking(
                clientId = uid("client"), clientName = name("client"),
                coachIds = listOf(uid("coach"), uid("coach2")), coachNames = listOf(name("coach"), name("coach2")),
                clientIds = listOf(uid("client"), uid("client2")), clientNames = listOf(name("client"), name("client2")),
                startAt = s.date("start"), endAt = s.date("end"), location = s.str("location"), notes = s.str("notes")
            ).getOrThrow()
        }
        r.register(id, "booking")
        checkpoint("requested")

        w.acceptBooking("coach").run { loadBooking(id); acceptBooking(id, s.dbl("coachRate"), null); assertNull(uiState.value.error) }
        checkpoint("coach_accepted")
        w.acceptBooking("coach2").run { loadBooking(id); acceptBooking(id, s.dbl("coach2Rate"), null); assertNull(uiState.value.error) }
        checkpoint("all_accepted")
        w.reviewBooking("client").run { loadBooking(id); confirmBooking(id); assertNull(uiState.value.error) }
        checkpoint("client_confirmed")
        w.reviewBooking("client2").run { loadBooking(id); confirmBooking(id); assertNull(uiState.value.error) }
        checkpoint("all_confirmed")

        val booking = io { w.bookings.fetchBooking(id, uid("coach"))!! }
        w.coachConfirmed("coach").run { cancelBooking(booking); assertNull(uiState.value.error) }
        checkpoint("cancelled")
    }

    @Test
    fun chat() = record("chat") {
        w.setUpActors("coach", "client")
        checkpoint("setup")

        val chatId = io { w.chats.createOrGetChat(uid("client"), uid("coach")) }
        r.register(chatId, "chat")
        checkpoint("opened")

        io { w.chats.sendMessage(chatId, uid("client"), s.str("message"), name("client")).getOrThrow() }
        val message = w.fake.docsIn("chats/$chatId/messages").keys.single()
        r.register(message, "message")
        checkpoint("messaged")

        io { w.chats.sendMessage(chatId, uid("coach"), s.str("reply"), name("coach")).getOrThrow() }
        r.register(w.fake.docsIn("chats/$chatId/messages").keys.single { it != message }, "reply")
        checkpoint("replied")
    }

    @Test
    fun club() = record("club") {
        w.setUpActors("coach", "client")
        checkpoint("setup")

        val club = io { w.places.createPlace(s.str("name"), s.str("address"), s.stringMap("playingTimes"), s.str("price"), uid("coach")).getOrThrow() }
        r.register(club, "club")
        checkpoint("created")

        // ClubManagementViewModel.toggleContact makes this same call
        io { w.places.assignPlaceContact(club, uid("coach"), name("coach")).getOrThrow() }
        checkpoint("claimed")

        io { w.places.requestMembership(club, uid("client"), name("client")).getOrThrow() }
        checkpoint("join_requested")

        w.clubManagement("coach").run { load(); approveMember(club, uid("client")) }
        checkpoint("approved")

        w.clubManagement("coach").run { load(); sendAnnouncement(club, s.str("name"), s.str("announcement")) }
        r.register(w.fake.docsIn("placesToPlay/$club/announcements").keys.single(), "announcement")
        checkpoint("announced")
    }

    @Test
    fun signup_event() = withDefaults(Story("signup_event").str("timeZone")) {
        record("signup_event") {
            w.setUpActors("coach", "client", "client2")
            val club = io { w.places.createPlace(s.str("club"), "100 Shuttle Ln, Minneapolis, MN", emptyMap(), null, uid("coach")).getOrThrow() }
            r.register(club, "club")
            checkpoint("setup")

            io {
                w.places.createEvent(
                    placeId = club, placeName = s.str("club"), title = s.str("title"), description = s.str("description"),
                    eventDate = s.date("eventDate"), location = s.str("location"), maxSignups = s.int("maxSignups"),
                    createdBy = uid("coach"), recurrence = s.str("recurrence"), feeUSD = s.dbl("feeUSD"),
                    paymentLink = s.str("paymentLink")
                ).getOrThrow()
            }
            val event = w.fake.docsIn("signupEvents").keys.single()
            r.register(event, "event")
            checkpoint("created")

            io { w.places.registerForEvent(event, uid("client"), name("client"), Actors.email("client")).getOrThrow() }
            checkpoint("signed_up")

            io { w.places.joinWaitlist(event, uid("client2"), name("client2"), Actors.email("client2")).getOrThrow() }
            checkpoint("waitlisted")

            io { w.places.unregisterFromEvent(event, uid("client")).getOrThrow() }
            checkpoint("promoted")

            io { w.places.createNextOccurrence(w.places.fetchAllEvents().single { it.id == event }).getOrThrow() }
            r.register(w.fake.docsIn("signupEvents").keys.single { it != event }, "nextEvent")
            checkpoint("next_week")
        }
    }

    @Test
    fun stringing() = record("stringing") {
        w.setUpActors("coach2", "client")
        checkpoint("setup")

        val profile = io {
            w.stringers.createOrUpdateStringer(
                BadmintonStringer(
                    userId = uid("coach2"), name = s.str("name"), strings = mapOf(s.str("string") to s.dbl("stringPrice")),
                    laborCost = s.dbl("laborCost"), meetupLocation = s.str("meetup")
                )
            ).getOrThrow()
        }
        r.register(profile, "stringerProfile")
        checkpoint("profile")

        // StringerDetailScreen places the order (and sends the stringer's push from the composable itself)
        io {
            w.stringers.placeOrder(
                stringerId = profile, stringerUserId = uid("coach2"), stringerName = s.str("name"), buyerId = uid("client"),
                buyerName = name("client"), racketName = s.str("racket"), hasOwnString = false, selectedString = s.str("string"),
                timelinePreference = s.str("timeline"), tension = s.int("tension"), stringCost = s.dbl("stringPrice"),
                laborCost = s.dbl("laborCost"), notes = null
            ).getOrThrow()
        }
        val order = w.fake.docsIn("stringerOrders").keys.single()
        r.register(order, "order")
        checkpoint("ordered")

        val detail = w.stringerOrder("coach2").apply { loadOrder(order) }
        detail.updateStatus("accepted")
        checkpoint("accepted")
        detail.updateStatus("ready_for_pickup")
        checkpoint("ready_for_pickup")
    }

    @Test
    fun review() = record("review") {
        w.setUpActors("coach", "client")
        checkpoint("setup")
        io {
            w.reviews.submitReview(
                ReviewItem(reviewerId = uid("client"), reviewerName = name("client"), coachId = uid("coach"),
                    rating = s.int("rating"), comment = s.str("comment"))
            ).getOrThrow()
        }
        r.register(w.fake.docsIn("reviews").keys.single(), "review")
        checkpoint("reviewed")
    }

    @Test
    fun tournament() = record("tournament") {
        w.setUpActors("coach", "client", "client2")
        checkpoint("setup")

        io { w.tournaments.createTournament(s.str("name"), s.date("start"), s.date("end"), s.str("location"), uid("coach"), s.str("signupLink")).getOrThrow() }
        val t = w.fake.docsIn("tournaments").keys.single()
        r.register(t, "tournament")
        checkpoint("created")

        fun join(actor: String) = io {
            val p = s.obj(actor)
            w.tournaments.joinTournament(
                t, uid(actor), p["gender"] as String, (p["events"] as List<*>).map { it as String },
                (p["skillLevels"] as List<*>).map { it as String }, name(actor)
            ).getOrThrow()
        }
        join("client")
        checkpoint("joined")
        join("client2")
        checkpoint("partner_joined")
    }

    @Test
    fun profiles() = record("profiles") {
        w.setUpActors("coach", "client")
        checkpoint("setup")

        val c = s.obj("coach")
        fun list(m: Map<*, *>, k: String) = (m[k] as List<*>).map { it as String }
        io {
            w.coaches.saveCoach(
                Coach(
                    id = uid("coach"), name = name("coach"), specialties = list(c, "specialties"),
                    experienceYears = (c["experienceYears"] as Number).toInt(), availability = list(c, "availability"),
                    bio = c["bio"] as String, hourlyRate = (c["hourlyRate"] as Number).toDouble(),
                    tournamentSoftwareLink = c["tournamentSoftwareLink"] as String, meetingPreference = c["meetingPreference"] as String,
                    zipCode = c["zipCode"] as String, city = c["city"] as String,
                    rateRange = (c["rateRange"] as List<*>).map { (it as Number).toDouble() },
                    cancellationWindowHours = (c["cancellationWindowHours"] as Number).toInt()
                )
            ).getOrThrow()
        }
        checkpoint("coach_profile")

        val cl = s.obj("client")
        io {
            w.clients.saveClient(
                Client(
                    id = uid("client"), name = name("client"), goals = list(cl, "goals"),
                    preferredAvailability = list(cl, "preferredAvailability"), meetingPreference = cl["meetingPreference"] as String,
                    skillLevel = cl["skillLevel"] as String, zipCode = cl["zipCode"] as String, city = cl["city"] as String,
                    bio = cl["bio"] as String
                )
            ).getOrThrow()
        }
        checkpoint("client_profile")

        // PaymentsViewModel.savePaymentHandle saves the merged map one handle at a time; the end state is this
        io { w.coaches.updatePayments(uid("coach"), s.stringMap("payments")).getOrThrow() }
        checkpoint("payment_methods")
    }
}
