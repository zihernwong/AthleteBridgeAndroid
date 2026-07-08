package com.athletebridge.android.util

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.athletebridge.android.data.model.BookingItem
import java.util.TimeZone

object CalendarHelper {

    /**
     * Silently inserts a booking into the device's primary calendar.
     * Must be called with WRITE_CALENDAR permission already granted.
     * Returns the new event row ID, or -1 on failure.
     */
    fun insertBookingEvent(context: Context, booking: BookingItem): Long {
        val start = booking.startAt ?: return -1
        val end   = booking.endAt   ?: return -1

        val calendarId = primaryCalendarId(context) ?: return -1

        val coachLabel = booking.allCoachNames.joinToString(", ").ifBlank { "Coach" }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "Badminton session with $coachLabel")
            put(CalendarContract.Events.DTSTART, start.time)
            put(CalendarContract.Events.DTEND, end.time)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

            booking.location?.takeIf { it.isNotBlank() }?.let {
                put(CalendarContract.Events.EVENT_LOCATION, it)
            }

            val descParts = buildList {
                booking.notes?.takeIf { it.isNotBlank() }?.let { add("Notes: $it") }
                booking.coachNote?.takeIf { it.isNotBlank() }?.let { add("Coach note: $it") }
            }
            if (descParts.isNotEmpty()) {
                put(CalendarContract.Events.DESCRIPTION, descParts.joinToString("\n"))
            }
        }

        return try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    private fun primaryCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        // Prefer the primary calendar; fall back to the first writable one
        for (selection in listOf(
            "${CalendarContract.Calendars.IS_PRIMARY} = 1",
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        )) {
            try {
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    selection,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) return cursor.getLong(0)
                }
            } catch (_: Exception) { }
        }
        return null
    }
}
