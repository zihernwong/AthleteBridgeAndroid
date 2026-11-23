package com.athletebridge.android.ui

import com.athletebridge.android.data.model.AwayTime
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.testutil.CHICAGO
import com.athletebridge.android.testutil.localDate
import com.athletebridge.android.testutil.withDefaults
import com.athletebridge.android.ui.bookings.TimeSlot
import com.athletebridge.android.ui.bookings.blockedByAwayTime
import com.athletebridge.android.ui.bookings.blockedByBooking
import com.athletebridge.android.ui.bookings.displayStatus
import com.athletebridge.android.ui.bookings.generateTimeSlots
import com.athletebridge.android.ui.bookings.openSlotCount
import com.athletebridge.android.ui.bookings.startOfWeekMillis
import com.athletebridge.android.ui.chat.formatChatTime
import com.athletebridge.android.ui.payments.SummaryRange
import com.athletebridge.android.ui.payments.filterInRange
import com.athletebridge.android.ui.payments.formatUSD
import com.athletebridge.android.ui.roster.lastSeenString
import com.athletebridge.android.ui.roster.relativeDateString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.athletebridge.android.ui.bookings.paymentDeepLink as bookingPaymentLink
import com.athletebridge.android.ui.payments.paymentDeepLink as paymentsPaymentLink

class PaymentLinkTest {

    @Test
    fun `Venmo handles open the Venmo profile`() {
        assertEquals("https://venmo.com/u/mei", paymentsPaymentLink("venmo", " @mei "))
        assertEquals("https://venmo.com/u/mei", bookingPaymentLink("Venmo", "@mei"))
    }

    @Test
    fun `Zelle and unknown platforms have no link`() {
        assertNull(paymentsPaymentLink("zelle", "612-555-0100"))
        assertNull(bookingPaymentLink("zelle", "612-555-0100"))
        assertNull(bookingPaymentLink("venmo", "   "))
    }

    @Test
    fun `booking screen Cash App link always carries the cashtag`() {
        assertEquals("https://cash.app/\$mei", bookingPaymentLink("cashapp", "\$mei"))
        assertEquals("https://cash.app/\$mei", bookingPaymentLink("Cash App", "mei"))
    }

    @Test
    fun `payments screen Cash App link carries the cashtag even when typed without one`() {
        assertEquals("https://cash.app/\$mei", paymentsPaymentLink("cashapp", "\$mei"))
        assertEquals("https://cash.app/\$mei", paymentsPaymentLink("cashapp", "mei"))
    }

    @Test
    fun `PayPal handle typed the way the app's placeholder suggests opens the right page`() {
        // PaymentsScreen's placeholder for PayPal is "e.g., paypal.me/username".
        assertEquals("https://paypal.me/mei", paymentsPaymentLink("paypal", "paypal.me/mei"))
        assertEquals("https://paypal.me/mei", bookingPaymentLink("paypal", "paypal.me/mei"))
    }

    @Test
    fun `plain PayPal usernames work`() {
        assertEquals("https://paypal.me/mei", paymentsPaymentLink("paypal", "mei"))
    }

    @Test
    fun `full profile URLs are reduced to the handle`() {
        assertEquals("https://paypal.me/mei", paymentsPaymentLink("paypal", "https://www.paypal.me/mei/"))
        assertEquals("https://venmo.com/u/mei", paymentsPaymentLink("venmo", "https://account.venmo.com/u/mei"))
        assertEquals("https://cash.app/\$mei", bookingPaymentLink("cashapp", "cash.app/\$mei"))
    }
}

class BookingStatusLabelTest {

    @Test
    fun `known statuses get friendly labels`() {
        assertEquals("Pending Acceptance", displayStatus("Pending Acceptance"))
        assertEquals("Pending Payment", displayStatus("pending_payment"))
        assertEquals("Payment Submitted", displayStatus("payment_submitted"))
        assertEquals("Declined By Client", displayStatus("declined_by_client"))
        assertEquals("Confirmed", displayStatus("CONFIRMED"))
    }

    @Test
    fun `unknown statuses are still readable`() {
        assertEquals("Fully confirmed", displayStatus("fully_confirmed"))
        assertEquals("Unknown", displayStatus(null))
    }
}

class PaymentSummaryRangeTest {

    private fun booking(date: Date?) = BookingItem(startAt = date)
    private val anyStart = Date(0)
    private val anyEnd = Date(0)

    /** Material3 DatePicker reports the chosen day as UTC midnight. */
    private fun pickedDay(year: Int, month: Int, day: Int): Date =
        Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant())

    @Test
    fun `last 30 and 90 days include recent sessions only`() {
        val tenDaysAgo = Date(System.currentTimeMillis() - 10L * 86_400_000)
        val sixtyDaysAgo = Date(System.currentTimeMillis() - 60L * 86_400_000)
        val nextWeek = Date(System.currentTimeMillis() + 7L * 86_400_000)
        assertTrue(filterInRange(booking(tenDaysAgo), SummaryRange.LAST_30, anyStart, anyEnd))
        assertFalse(filterInRange(booking(sixtyDaysAgo), SummaryRange.LAST_30, anyStart, anyEnd))
        assertTrue(filterInRange(booking(sixtyDaysAgo), SummaryRange.LAST_90, anyStart, anyEnd))
        assertFalse(filterInRange(booking(nextWeek), SummaryRange.LAST_90, anyStart, anyEnd))
        assertFalse(filterInRange(booking(null), SummaryRange.LAST_90, anyStart, anyEnd))
    }

    @Test
    fun `year to date starts on January 1st`() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val lastYear = Calendar.getInstance().apply { set(year - 1, Calendar.DECEMBER, 31, 12, 0) }.time
        assertFalse(filterInRange(booking(lastYear), SummaryRange.YTD, anyStart, anyEnd))
        assertTrue(filterInRange(booking(Date(System.currentTimeMillis() - 1000)), SummaryRange.YTD, anyStart, anyEnd))
    }

    @Test
    fun `custom range includes sessions on the chosen end day`() {
        withDefaults(CHICAGO) {
            val start = pickedDay(2026, 11, 1)
            val end = pickedDay(2026, 11, 10)
            val morningOfEndDay = localDate(CHICAGO, 2026, 11, 10, 10, 0)
            assertTrue("10 AM on the chosen end day was left out", filterInRange(booking(morningOfEndDay), SummaryRange.CUSTOM, start, end))
        }
    }

    @Test
    fun `custom range excludes sessions from the evening before the chosen start day`() {
        withDefaults(CHICAGO) {
            val start = pickedDay(2026, 11, 1)
            val end = pickedDay(2026, 11, 10)
            val eveningBefore = localDate(CHICAGO, 2026, 10, 31, 20, 0)
            assertFalse("8 PM the day before the start was counted", filterInRange(booking(eveningBefore), SummaryRange.CUSTOM, start, end))
        }
    }

    @Test
    fun `currency formatting`() {
        withDefaults(CHICAGO, Locale.US) {
            assertEquals("$12.50", formatUSD(12.5))
            assertEquals("$1500", formatUSD(1500.0))
        }
    }
}

class BookingSlotLogicTest {

    private val day = localDate(CHICAGO, 2026, 12, 7) // a Monday

    private fun booking(startHour: Int, startMin: Int, endHour: Int, endMin: Int, status: String?) = BookingItem(
        startAt = localDate(CHICAGO, 2026, 12, 7, startHour, startMin),
        endAt = localDate(CHICAGO, 2026, 12, 7, endHour, endMin),
        status = status
    )

    private fun slotAt(hour: Int, minute: Int): TimeSlot =
        withDefaults(CHICAGO) { generateTimeSlots(day.time) }
            .single { it.startMillis == localDate(CHICAGO, 2026, 12, 7, hour, minute).time }

    @Test
    fun `day grid has half-hour slots from 6 AM to 10 PM`() {
        withDefaults(CHICAGO) {
            val slots = generateTimeSlots(day.time)
            assertEquals(32, slots.size)
            assertEquals("6:00 AM", slots.first().label)
            assertEquals("9:30 PM", slots.last().label)
            assertTrue(slots.all { it.endMillis - it.startMillis == 30 * 60_000L })
        }
    }

    @Test
    fun `grid still starts at 6 AM on the day clocks change`() {
        withDefaults(CHICAGO) {
            val fallBack = localDate(CHICAGO, 2026, 11, 1)
            val slots = generateTimeSlots(fallBack.time)
            assertEquals(localDate(CHICAGO, 2026, 11, 1, 6, 0).time, slots.first().startMillis)
            assertEquals(32, slots.size)
        }
    }

    @Test
    fun `active bookings block overlapping slots, finished-off ones do not`() {
        val confirmed = booking(18, 0, 19, 0, "confirmed")
        assertEquals(confirmed, blockedByBooking(slotAt(18, 0), listOf(confirmed)))
        assertEquals(confirmed, blockedByBooking(slotAt(18, 30), listOf(confirmed)))
        assertNull("touching edges don't overlap", blockedByBooking(slotAt(19, 0), listOf(confirmed)))
        assertNull(blockedByBooking(slotAt(17, 30), listOf(confirmed)))
        for (s in listOf("cancelled", "declined", "declined_by_client", "Rejected")) {
            assertNull(s, blockedByBooking(slotAt(18, 0), listOf(booking(18, 0, 19, 0, s))))
        }
        assertEquals("requested bookings still hold the slot", 1,
            listOfNotNull(blockedByBooking(slotAt(18, 0), listOf(booking(18, 0, 19, 0, "requested")))).size)
    }

    @Test
    fun `away time blocks slots`() {
        val away = AwayTime(startAt = localDate(CHICAGO, 2026, 12, 7, 12, 0), endAt = localDate(CHICAGO, 2026, 12, 7, 13, 0))
        assertEquals(away, blockedByAwayTime(slotAt(12, 30), listOf(away)))
        assertNull(blockedByAwayTime(slotAt(13, 0), listOf(away)))
    }

    @Test
    fun `open slot count ignores the past and subtracts blocked slots`() {
        withDefaults(CHICAGO) {
            val yesterday = System.currentTimeMillis() - 86_400_000
            assertEquals(0, openSlotCount(yesterday, emptyList(), emptyList()))
            val farFuture = localDate(CHICAGO, 2030, 3, 4)
            val busy = BookingItem(
                startAt = localDate(CHICAGO, 2030, 3, 4, 18, 0), endAt = localDate(CHICAGO, 2030, 3, 4, 19, 0), status = "confirmed"
            )
            assertEquals(30, openSlotCount(farFuture.time, listOf(busy), emptyList()))
        }
    }

    @Test
    fun `week strip starts on the locale's first day of the week`() {
        val wednesday = localDate(CHICAGO, 2026, 12, 9, 15, 0)
        withDefaults(CHICAGO, Locale.US) {
            assertEquals(localDate(CHICAGO, 2026, 12, 6).time, startOfWeekMillis(wednesday.time)) // Sunday
        }
        withDefaults(CHICAGO, Locale.UK) {
            assertEquals(localDate(CHICAGO, 2026, 12, 7).time, startOfWeekMillis(wednesday.time)) // Monday
        }
        withDefaults(CHICAGO, Locale.US) {
            val newYearsDay = localDate(CHICAGO, 2027, 1, 1, 9, 0) // Friday
            assertEquals(localDate(CHICAGO, 2026, 12, 27).time, startOfWeekMillis(newYearsDay.time))
        }
    }
}

class ChatTimeLabelTest {

    @Test
    fun `messages from today show the time`() {
        val now = Date()
        assertEquals(SimpleDateFormat("h:mm a", Locale.getDefault()).format(now), formatChatTime(now))
    }

    @Test
    fun `messages from another year show the date`() {
        val lastYear = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }.time
        assertEquals(SimpleDateFormat("MMM d", Locale.getDefault()).format(lastYear), formatChatTime(lastYear))
    }

    @Test
    fun `a message from the same day number in another month shows the date, not a time`() {
        // e.g. on Nov 23, a message from Oct 23 must read "Oct 23", not "4:15 PM".
        val today = Calendar.getInstance()
        val otherMonth = (0..11).asSequence()
            .filter { it != today.get(Calendar.MONTH) }
            .map { m -> Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.MONTH, m) } }
            .first { it.getActualMaximum(Calendar.DAY_OF_MONTH) >= today.get(Calendar.DAY_OF_MONTH) }
            .apply { set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH)); set(Calendar.HOUR_OF_DAY, 16); set(Calendar.MINUTE, 15) }
            .time
        assertEquals(SimpleDateFormat("MMM d", Locale.getDefault()).format(otherMonth), formatChatTime(otherMonth))
    }
}

class RosterLabelTest {

    private fun daysAgo(days: Int) = Date(System.currentTimeMillis() - days * 86_400_000L - 60_000)

    @Test
    fun `last seen labels`() {
        val now = Date()
        assertEquals("5m ago", lastSeenString(300, now))
        assertEquals("2h ago", lastSeenString(7_200, now))
        assertEquals("yesterday", lastSeenString(100_000, now))
        assertEquals("3d ago", lastSeenString(3 * 86_400L, now))
    }

    @Test
    fun `last session labels for past sessions`() {
        assertEquals("today", relativeDateString(Date(System.currentTimeMillis() - 60_000)))
        assertEquals("yesterday", relativeDateString(daysAgo(1)))
        assertEquals("3d ago", relativeDateString(daysAgo(3)))
        assertEquals("2w ago", relativeDateString(daysAgo(15)))
    }

    @Test
    fun `an upcoming session is not labelled as today`() {
        // RosterViewModel's lastSessionDate is the latest booking, which is often next week's session.
        val nextWeek = Date(System.currentTimeMillis() + 7 * 86_400_000L)
        assertNotEquals("today", relativeDateString(nextWeek))
    }

    @Test
    fun `upcoming sessions read naturally`() {
        fun noonIn(days: Long) = Date.from(
            LocalDate.now().plusDays(days).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
        )
        assertEquals("tomorrow", relativeDateString(noonIn(1)))
        assertEquals("in 3d", relativeDateString(noonIn(3)))
        assertEquals("in 2w", relativeDateString(noonIn(14)))
    }
}
