package com.athletebridge.android.crossplatform

import com.athletebridge.android.testutil.FakeFirestore
import org.junit.Assert.assertEquals
import org.junit.Test

/** contract.json "pushRouting": every push type either app sends must open the agreed screen on Android. */
class PushRoutingContractTest {

    @Test
    fun `every push type in the contract opens the agreed screen on Android`() {
        val seed = Seeder(FakeFirestore(), AndroidWorld.READING_UIDS)
        val problems = PushRoutes.routes.mapNotNull { route ->
            val notification: Map<String, Any?> = route.data.mapValues { seed.text(it.value) } + ("type" to route.type)
            PushRoutes.problem(notification, seed.id("client"))
        }
        assertEquals("Push types that open the wrong screen (or nothing) on Android", emptyList<String>(), problems)
    }

    @Test
    fun `the Cloud Function forwards every id a router reads`() {
        // Both apps read these from a push to open the right screen; a key the function drops never arrives.
        val readByRouters = setOf("chatId", "bookingId", "stringerOrderId", "placeId", "announcementId", "eventId", "type")
        assertEquals(
            "Keys the apps route on that onPendingNotificationCreate doesn't forward",
            emptySet<String>(), readByRouters - CloudFunction.forwardedKeys.toSet()
        )
    }
}
