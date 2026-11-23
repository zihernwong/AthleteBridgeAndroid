package com.athletebridge.android.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.athletebridge.android.AppRefreshBus
import com.athletebridge.android.AthleteBridgeApplication
import com.athletebridge.android.DeepLinkBus
import com.athletebridge.android.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "=== onNewToken fired === token=${token.take(20)}...")
        // Always persist the token locally so it survives a pre-login token refresh
        savePendingToken(this, token)
        Log.d(TAG, "onNewToken: token saved to SharedPreferences")

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        Log.d(TAG, "onNewToken: currentUser uid=$uid")
        if (uid == null) {
            Log.d(TAG, "onNewToken: user not logged in yet — token will be flushed on next login")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "onNewToken: saving token to Firestore for uid=$uid")
            saveTokenToFirestore(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "=== onMessageReceived === from=${message.from}")
        Log.d(TAG, "  notification block: title=${message.notification?.title} body=${message.notification?.body}")
        Log.d(TAG, "  data payload: ${message.data}")

        val title = message.notification?.title ?: message.data["title"] ?: "AthleteBridge"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val deepLinkUri = buildDeepLinkUri(message.data)
        Log.d(TAG, "  resolved title='$title' body='$body' deepLink=$deepLinkUri")

        showNotification(title, body, deepLinkUri, message.data["type"])

        // Signal foreground screens to refresh their data
        val refreshEvent = when (message.data["type"]) {
            "stringer_order" -> AppRefreshBus.Event.STRINGER_ORDERS
            "booking_request", "booking_accepted", "booking_confirmed",
            "booking_cancelled", "booking_rejected", "booking_rescheduled",
            "payment_submitted", "payment_acknowledged" -> AppRefreshBus.Event.BOOKINGS
            else -> AppRefreshBus.Event.ALL
        }
        AppRefreshBus.emit(refreshEvent)
        Log.d(TAG, "  AppRefreshBus emitted $refreshEvent")
    }

    /**
     * Maps the FCM data payload to a deep link URI.
     * Expected keys: type, chatId, bookingId, stringerOrderId (orderId fallback), placeId, announcementId
     *
     * Coach-targeted: booking_request, booking_confirmed, payment_submitted, club_join_request
     * Client-targeted: booking_accepted, booking_rejected, booking_rescheduled, booking_declined,
     *                  payment_acknowledged, payment_confirmed, payment_reminder, club_approved, club_announcement
     * Either: booking_cancelled → bookings tab
     */
    private fun buildDeepLinkUri(data: Map<String, String>): Uri? =
        deepLinkFor(data)?.let { Uri.parse(it) }

    /** Picks the appropriate notification channel based on FCM message type. */
    private fun channelForType(type: String?): String = when (type) {
        "chat" -> AthleteBridgeApplication.CHANNEL_MESSAGES
        "booking_request", "booking_accepted", "booking_confirmed",
        "booking_rejected", "booking_rescheduled", "booking_declined",
        "booking_cancelled" -> AthleteBridgeApplication.CHANNEL_BOOKINGS
        "payment_submitted", "payment_acknowledged",
        "payment_confirmed", "payment_reminder" -> AthleteBridgeApplication.CHANNEL_PAYMENTS
        "club_join_request", "club_approved", "club_announcement" -> AthleteBridgeApplication.CHANNEL_CLUBS
        else -> AthleteBridgeApplication.CHANNEL_BOOKINGS
    }

    private fun showNotification(title: String, body: String, deepLinkUri: Uri?, type: String? = null) {
        Log.d(TAG, "=== showNotification === title='$title' body='$body' deepLink=$deepLinkUri")
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelId = channelForType(type)
        val channel = notificationManager.getNotificationChannel(channelId)
        Log.d(TAG, "  channel '$channelId' exists=${channel != null} importance=${channel?.importance}")

        // Post to DeepLinkBus so the NavHost can consume it even if the activity
        // is in the foreground and onResume won't fire again.
        if (deepLinkUri != null) DeepLinkBus.post(deepLinkUri)

        val pendingIntent = if (deepLinkUri != null) {
            // Explicit MainActivity intent with deep link URI — more reliable than TaskStackBuilder
            // when there is no parent activity hierarchy to reconstruct.
            val intent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = deepLinkUri
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            PendingIntent.getActivity(
                this,
                deepLinkUri.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notifId = System.currentTimeMillis().toInt()
        notificationManager.notify(notifId, notification)
        Log.d(TAG, "  notificationManager.notify() called with id=$notifId")
    }

    // Channels are created at app startup in AthleteBridgeApplication

    companion object {
        private const val TAG = "NotificationService"
        private const val PREFS_NAME = "fcm_prefs"
        private const val KEY_PENDING_TOKEN = "pending_token"

        /**
         * Maps a push's data payload to a deep link. Used for foreground pushes here and for taps on
         * background pushes in MainActivity, so both open the same screen.
         *
         * Booking types describe the event, not the recipient ("booking_confirmed" goes to coaches and
         * clients alike), so the booking links below are resolved to the coach or client screen by role
         * in navigation (see deepLinkRoute).
         */
        internal fun deepLinkFor(data: Map<String, String>): String? {
            val type = data["type"]
            val bookingId = data["bookingId"]
            val placeId = data["placeId"]
            // A chat id always means "open that conversation"
            data["chatId"]?.let { return "athletebridge://chat/$it" }
            return when (type) {
                "booking_request",
                "booking_confirmed",
                "payment_submitted",
                "payment" /* sent by older Android builds */ -> bookingId?.let { "athletebridge://accept_booking/$it" }
                "booking_accepted",
                "booking_rejected",
                "booking_rescheduled",
                "booking_declined",
                "payment_acknowledged" -> bookingId?.let { "athletebridge://review_booking/$it" }
                // Payment notifications → payments tab
                "payment_confirmed",
                "payment_reminder" -> "athletebridge://payments"
                // Cancelled/rejected — go to bookings tab
                "booking_cancelled" -> "athletebridge://bookings"
                // Stringer orders — iOS uses "stringerOrderId" key (and older iOS builds these type names)
                "stringer_order", "stringer_order_placed", "stringer_order_update" ->
                    (data["stringerOrderId"] ?: data["orderId"])?.let { "athletebridge://stringer_order_detail/$it" }
                // Club notifications
                "club_join_request" -> placeId?.let { "athletebridge://place_detail/$it" }
                "club_approved" -> placeId?.let { "athletebridge://my_clubs" }
                "club_announcement" -> placeId?.let { "athletebridge://club_announcements/$it" }
                // Moved off a waitlist: open the event when its id is known, otherwise the club's events
                "event_waitlist_promoted" -> placeId?.let { pid ->
                    data["eventId"]?.let { "athletebridge://signup_event_detail/$pid/$it" }
                        ?: "athletebridge://signup_events/$pid"
                }
                "tournament_partner", "tournament_recommendation" -> "athletebridge://tournament_partner"
                // Untyped or unknown pushes (older iOS builds sent booking pushes without a type):
                // still open what the payload points at. Booking links resolve to the role's screen.
                else -> bookingId?.let { "athletebridge://accept_booking/$it" }
                    ?: (data["stringerOrderId"] ?: data["orderId"])?.let { "athletebridge://stringer_order_detail/$it" }
            }
        }

        /** Saves the token to SharedPreferences so it survives a pre-login token refresh. */
        fun savePendingToken(context: Context, token: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_PENDING_TOKEN, token).apply()
        }

        /** Returns the locally-stored pending token, if any. */
        fun getPendingToken(context: Context): String? =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PENDING_TOKEN, null)

        /** Clears the locally-stored pending token after it has been saved to Firestore. */
        fun clearPendingToken(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_PENDING_TOKEN).apply()
        }

        /** Writes the token to the coach or client document in Firestore. */
        suspend fun saveTokenToFirestore(uid: String, token: String) {
            Log.d(TAG, "saveTokenToFirestore: uid=$uid token=${token.take(20)}...")
            try {
                val db = FirebaseFirestore.getInstance()
                Log.d(TAG, "saveTokenToFirestore: fetching userType doc...")
                val userTypeDoc = db.collection("userType").document(uid).get().await()
                val userType = userTypeDoc.getString("type")
                Log.d(TAG, "saveTokenToFirestore: userType=$userType exists=${userTypeDoc.exists()}")
                val collection = if (userType?.uppercase() == "COACH") "coaches" else "clients"
                Log.d(TAG, "saveTokenToFirestore: writing to $collection/$uid deviceTokens...")
                db.collection(collection).document(uid)
                    .set(mapOf("deviceTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
                    .await()
                Log.d(TAG, "saveTokenToFirestore: SUCCESS — token saved to $collection/$uid")
            } catch (e: Exception) {
                Log.e(TAG, "saveTokenToFirestore: FAILED uid=$uid", e)
            }
        }
    }
}
