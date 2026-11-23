package com.athletebridge.android.testutil

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Routes viewModelScope (Dispatchers.Main) to an eager test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

const val CHICAGO = "America/Chicago"

/** Run [block] with the JVM default time zone and locale swapped, restoring both afterwards. */
inline fun <T> withDefaults(zone: String, locale: Locale = Locale.US, block: () -> T): T {
    val savedZone = TimeZone.getDefault()
    val savedLocale = Locale.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone(zone))
    Locale.setDefault(locale)
    try {
        return block()
    } finally {
        TimeZone.setDefault(savedZone)
        Locale.setDefault(savedLocale)
    }
}

fun localDate(zone: String, year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Date =
    Date.from(ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(zone)).toInstant())

fun localHour(zone: String, date: Date): Int = date.toInstant().atZone(ZoneId.of(zone)).hour

fun ts(date: Date): Timestamp = Timestamp(date)

fun hoursFromNow(hours: Double): Date = Date(System.currentTimeMillis() + (hours * 3_600_000).toLong())

fun fakeUser(uid: String, name: String? = null, email: String? = null): FirebaseUser = mockk {
    every { this@mockk.uid } returns uid
    every { displayName } returns name
    every { this@mockk.email } returns email
}
