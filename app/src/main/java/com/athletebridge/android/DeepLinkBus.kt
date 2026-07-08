package com.athletebridge.android

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bus that holds a pending deep-link URI until the NavHost is
 * ready to consume it. This bridges the gap when auth loading delays
 * NavHost composition past the activity's first onResume.
 */
object DeepLinkBus {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    fun post(uri: Uri) { _pending.value = uri }
    fun consume() { _pending.value = null }
}
