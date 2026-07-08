package com.athletebridge.android.util

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.athletebridge.android.data.model.BookingItem
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun scheduleReminder(context: Context, booking: BookingItem, coachName: String) {
        val startMillis = booking.startAt?.time ?: return
        val delay = startMillis - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        if (delay <= 0) return
        val data = workDataOf(
            "title" to "Session Reminder",
            "message" to "Your session with $coachName starts in 1 hour.",
            "notificationId" to booking.id.hashCode()
        )
        val request = OneTimeWorkRequestBuilder<BookingReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("booking_reminder_${booking.id}")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "booking_reminder_${booking.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(context: Context, bookingId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("booking_reminder_$bookingId")
    }
}
