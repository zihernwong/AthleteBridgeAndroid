package com.athletebridge.android.data.model

sealed class DeepLinkDestination {
    data class Chat(val chatId: String) : DeepLinkDestination()
    data class Booking(val bookingId: String, val type: String? = null) : DeepLinkDestination()
    data object Payments : DeepLinkDestination()
}
