package com.athletebridge.android.crossplatform

import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.AwayTimeRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.ChatRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.athletebridge.android.data.repository.PlaceRepository
import com.athletebridge.android.data.repository.ReviewRepository
import com.athletebridge.android.data.repository.StringerRepository
import com.athletebridge.android.data.repository.TournamentRepository
import com.athletebridge.android.data.repository.UserDirectoryRepository
import com.athletebridge.android.testutil.FakeFirestore
import com.athletebridge.android.testutil.fakeUser
import com.athletebridge.android.ui.bookings.AcceptBookingViewModel
import com.athletebridge.android.ui.bookings.BookingDetailViewModel
import com.athletebridge.android.ui.bookings.BookingEditorViewModel
import com.athletebridge.android.ui.bookings.ClientConfirmedBookingsViewModel
import com.athletebridge.android.ui.bookings.CoachConfirmedBookingsViewModel
import com.athletebridge.android.ui.bookings.ReviewBookingViewModel
import com.athletebridge.android.ui.places.ClubManagementViewModel
import com.athletebridge.android.ui.stringing.StringerOrderDetailViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * The Android app's real repositories and view models on an in-memory Firestore, with one signed-in user per
 * contract actor. Callers need MainDispatcherRule (view models launch on Dispatchers.Main) and ReminderScheduler
 * mocked (confirming a booking schedules an Android alarm).
 */
class AndroidWorld(val uids: Map<String, String>, recording: Boolean = false) {
    // Recording needs a server clock that moves on with every write, so the steps' times keep their order
    val fake = FakeFirestore(serverTime = Timestamp.now()).apply { advanceServerTime = recording }
    private val db get() = fake.instance
    private val storage = mockk<FirebaseStorage>(relaxed = true)

    val bookings = BookingRepository(db)
    val coaches = CoachRepository(db, storage)
    val clients = ClientRepository(db, storage)
    val places = PlaceRepository(db)
    val chats = ChatRepository(db, storage)
    val stringers = StringerRepository(db)
    val reviews = ReviewRepository(db)
    val tournaments = TournamentRepository(db)
    val notifications = NotificationRepository(db, mockk(relaxed = true))
    private val awayTimes = AwayTimeRepository(db)
    private val directory = UserDirectoryRepository(db, storage)

    fun uid(actor: String) = uids.getValue(actor)
    private fun user(actor: String) = fakeUser(uid(actor), Actors.name(actor), Actors.email(actor))
    fun auth(actor: String): AuthRepository = mockk { every { currentUser } returns user(actor) }
    private fun firebaseAuth(actor: String): FirebaseAuth = mockk(relaxed = true) { every { currentUser } returns user(actor) }

    fun bookingEditor(actor: String) = BookingEditorViewModel(bookings, awayTimes, coaches, clients, auth(actor), notifications, places)
    fun acceptBooking(actor: String) = AcceptBookingViewModel(bookings, coaches, auth(actor), notifications)
    fun reviewBooking(actor: String) = ReviewBookingViewModel(bookings, coaches, auth(actor), notifications, mockk(relaxed = true))
    fun coachConfirmed(actor: String) = CoachConfirmedBookingsViewModel(bookings, auth(actor), notifications)
    fun clientConfirmed(actor: String) = ClientConfirmedBookingsViewModel(bookings, auth(actor), coaches, notifications)
    fun bookingDetail(actor: String) = BookingDetailViewModel(bookings, auth(actor), coaches, clients, notifications)
    fun clubManagement(actor: String) = ClubManagementViewModel(places, clients, coaches, directory, notifications, firebaseAuth(actor))
    fun stringerOrder(actor: String) = StringerOrderDetailViewModel(stringers, notifications, firebaseAuth(actor))

    /** userType and profile documents as Android sign-up (AuthRepository.signUp) and the profile screen write them. */
    fun setUpActors(vararg actors: String) = runBlocking {
        for (a in actors) {
            db.collection("userType").document(uid(a)).set(mapOf("type" to Actors.role(a))).await()
            if (Actors.role(a) == "COACH") coaches.saveCoach(Coach(id = uid(a), name = Actors.name(a), specialties = listOf("Badminton"))).getOrThrow()
            else clients.saveClient(Client(id = uid(a), name = Actors.name(a))).getOrThrow()
        }
    }

    /** Push documents queued for [actor] (the Cloud Function turns each into a push). */
    fun pushes(actor: String): List<Map<String, Any?>> =
        fake.docsIn("pendingNotifications/${uid(actor)}/notifications").values.toList()

    companion object {
        /** Ids for actors when Android records its own stories. */
        val RECORDING_UIDS = mapOf("coach" to "a-coach-mei", "coach2" to "a-coach2-raj", "client" to "a-client-alex", "client2" to "a-client2-sam")
        /** Ids for actors when Android reads another app's fixture. */
        val READING_UIDS = mapOf("coach" to "uid-coach", "coach2" to "uid-coach2", "client" to "uid-client", "client2" to "uid-client2")
    }
}
