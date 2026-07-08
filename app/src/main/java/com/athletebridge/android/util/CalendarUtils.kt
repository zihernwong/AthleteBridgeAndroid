package com.athletebridge.android.util

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.athletebridge.android.data.model.BookingItem
import java.util.TimeZone

fun addBookingToCalendar(context: Context, booking: BookingItem, coachName: String) {
    val startMillis = booking.startAt?.time ?: return
    val endMillis = booking.endAt?.time ?: return
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        putExtra(CalendarContract.Events.TITLE, "Session with $coachName")
        putExtra(CalendarContract.Events.DESCRIPTION, booking.notes ?: "")
        putExtra(CalendarContract.Events.EVENT_LOCATION, booking.location ?: "")
        putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        putExtra(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
    }
    context.startActivity(intent)
}
