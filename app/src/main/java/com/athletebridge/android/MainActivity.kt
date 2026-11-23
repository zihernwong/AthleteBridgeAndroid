package com.athletebridge.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.athletebridge.android.DeepLinkBus
import com.athletebridge.android.service.NotificationService
import com.athletebridge.android.ui.navigation.AppNavigation
import com.athletebridge.android.ui.theme.AthleteBridgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(FCM_TAG, "POST_NOTIFICATIONS permission result: granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        // Convert FCM data extras → deep link URI so the NavHost navigates correctly
        // when the app is launched by tapping a background/killed-app notification.
        applyFcmDeepLink(intent)
        // Handle app shortcut intents
        handleShortcutIntent(intent)
        setContent {
            AthleteBridgeTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.athletebridge.android.AppRefreshBus.emit(com.athletebridge.android.AppRefreshBus.Event.ALL)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            Log.d(FCM_TAG, "POST_NOTIFICATIONS already granted=$granted (Android ${Build.VERSION.SDK_INT})")
            if (!granted) {
                Log.d(FCM_TAG, "POST_NOTIFICATIONS: requesting permission...")
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            Log.d(FCM_TAG, "POST_NOTIFICATIONS: not needed on Android ${Build.VERSION.SDK_INT} (pre-33)")
        }
    }

    companion object {
        private const val FCM_TAG = "FCM_DEBUG"
    }

    // Forward notification deep link intents when the app is already running.
    // Also handles background FCM notification taps (data extras → deep link URI).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyFcmDeepLink(intent)
        handleShortcutIntent(intent)
        setIntent(intent)
    }

    /**
     * Handles launcher shortcut intents by posting a deep link URI to DeepLinkBus.
     * Shortcut actions: ACTION_MESSAGES → athletebridge://messages
     *                   ACTION_BOOKINGS → athletebridge://bookings
     */
    private fun handleShortcutIntent(intent: Intent) {
        val uri = when (intent.action) {
            "com.athletebridge.android.ACTION_MESSAGES" ->
                android.net.Uri.parse("athletebridge://messages")
            "com.athletebridge.android.ACTION_BOOKINGS" ->
                android.net.Uri.parse("athletebridge://bookings")
            else -> null
        }
        if (uri != null) {
            Log.d(FCM_TAG, "handleShortcutIntent: action=${intent.action} → $uri")
            DeepLinkBus.post(uri)
        }
    }

    /**
     * When a background/killed-app FCM notification is tapped, Android delivers the
     * message's data payload as Intent extras (not as intent.data). This converts
     * those extras into a deep link URI and posts it to DeepLinkBus so the NavHost
     * can consume it even if it isn't composed yet (due to auth loading delay).
     * If the intent already has a URI (e.g. from a foreground notification PendingIntent)
     * it is posted directly without re-parsing.
     */
    private fun applyFcmDeepLink(intent: Intent) {
        // Foreground notification PendingIntent already has intent.data set.
        if (intent.data != null) {
            Log.d(FCM_TAG, "applyFcmDeepLink: intent already has URI ${intent.data}, posting to bus")
            DeepLinkBus.post(intent.data!!)
            // Clear the URI so the NavHost doesn't ALSO handle it (double navigation) and,
            // more importantly, doesn't re-handle the stale intent on every activity
            // recreation — which kept dumping users onto the Bookings tab instead of Home.
            intent.data = null
            return
        }

        val uri: Uri? = fcmExtrasDeepLink(
            type = intent.getStringExtra("type"),
            chatId = intent.getStringExtra("chatId"),
            bookingId = intent.getStringExtra("bookingId"),
            // iOS sends "stringerOrderId"; fall back to "orderId" for Android-created messages
            orderId = intent.getStringExtra("stringerOrderId") ?: intent.getStringExtra("orderId"),
            placeId = intent.getStringExtra("placeId"),
            eventId = intent.getStringExtra("eventId")
        )?.let { Uri.parse(it) }

        if (uri != null) {
            Log.d(FCM_TAG, "applyFcmDeepLink: FCM extras → deep link $uri")
            // Post to the bus only (consume-once). Setting intent.data here made the
            // NavHost re-handle the deep link on every recreation, hijacking navigation.
            DeepLinkBus.post(uri)
        }
    }
}

/** Background-notification extras → deep link, using the same mapping as foreground pushes. */
internal fun fcmExtrasDeepLink(
    type: String?,
    chatId: String?,
    bookingId: String?,
    orderId: String?,
    placeId: String?,
    eventId: String? = null
): String? = NotificationService.deepLinkFor(
    buildMap {
        type?.let { put("type", it) }
        chatId?.let { put("chatId", it) }
        bookingId?.let { put("bookingId", it) }
        orderId?.let { put("stringerOrderId", it) }
        placeId?.let { put("placeId", it) }
        eventId?.let { put("eventId", it) }
    }
)
