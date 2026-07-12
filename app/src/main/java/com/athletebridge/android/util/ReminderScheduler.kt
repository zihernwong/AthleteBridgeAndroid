package com.athletebridge.android.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.athletebridge.android.data.model.BookingItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    /** Schedules reminders 24 hours and 1 hour before the session (matching iOS). */
    fun scheduleReminder(context: Context, booking: BookingItem, coachName: String) {
        val startMillis = booking.startAt?.time ?: return
        val timeLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(startMillis))

        scheduleAt(
            context = context,
            workName = "booking_reminder_24h_${booking.id}",
            delayMillis = startMillis - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24),
            message = "Your session with $coachName is tomorrow at $timeLabel.",
            notificationId = ("24h" + booking.id).hashCode()
        )
        scheduleAt(
            context = context,
            workName = "booking_reminder_${booking.id}",
            delayMillis = startMillis - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1),
            message = "Your session with $coachName starts in 1 hour.",
            notificationId = booking.id.hashCode()
        )
    }

    private fun scheduleAt(
        context: Context,
        workName: String,
        delayMillis: Long,
        message: String,
        notificationId: Int
    ) {
        if (delayMillis <= 0) return
        val data = workDataOf(
            "title" to "Session Reminder",
            "message" to message,
            "notificationId" to notificationId
        )
        val request = OneTimeWorkRequestBuilder<BookingReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(workName)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(context: Context, bookingId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("booking_reminder_$bookingId")
        WorkManager.getInstance(context).cancelUniqueWork("booking_reminder_24h_$bookingId")
    }
}
