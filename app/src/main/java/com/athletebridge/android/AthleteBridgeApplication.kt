package com.athletebridge.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.athletebridge.android.util.setupAppShortcuts
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AthleteBridgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        // Crashlytics — disable collection in debug builds
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        createNotificationChannels()

        setupAppShortcuts(this)
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val channels = listOf(
            NotificationChannel(
                CHANNEL_BOOKINGS,
                "Bookings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Booking requests, confirmations, and updates" },
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "New chat messages" },
            NotificationChannel(
                CHANNEL_PAYMENTS,
                "Payments",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Payment notifications" },
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Session Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Upcoming session reminders" },
            NotificationChannel(
                CHANNEL_CLUBS,
                "Clubs & Events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Club announcements and event updates" },
            // Legacy channel kept for backward compatibility
            NotificationChannel(
                CHANNEL_ID_LEGACY,
                "AthleteBridge Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Booking and chat notifications" }
        )

        channels.forEach { channel ->
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Created/updated notification channel: ${channel.id}")
        }
    }

    companion object {
        /** Legacy single channel — kept for backward compatibility */
        const val CHANNEL_ID_LEGACY = "athletebridge_default"

        // Granular channels
        const val CHANNEL_BOOKINGS = "bookings"
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_PAYMENTS = "payments"
        const val CHANNEL_REMINDERS = "reminders"
        const val CHANNEL_CLUBS = "clubs"

        private const val TAG = "FCM_DEBUG"
    }
}
