package com.athletebridge.android

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide event bus for triggering foreground screen refreshes when an
 * FCM notification is received while the app is open.
 *
 * Usage:
 *   Emit  → AppRefreshBus.emit(AppRefreshBus.Event.STRINGER_ORDERS)
 *   Collect → AppRefreshBus.events.collect { … }
 */
object AppRefreshBus {
    enum class Event { STRINGER_ORDERS, BOOKINGS, ALL }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun emit(event: Event) {
        _events.tryEmit(event)
    }
}
