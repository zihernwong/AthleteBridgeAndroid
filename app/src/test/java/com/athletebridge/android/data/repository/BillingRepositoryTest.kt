package com.athletebridge.android.data.repository

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.athletebridge.android.data.model.CoachTier
import com.athletebridge.android.testutil.FakeFirestore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BillingRepositoryTest {

    private val fake = FakeFirestore()
    private lateinit var repo: BillingRepository

    @Before
    fun setUp() {
        mockkStatic(BillingClient::class)
        val builder = mockk<BillingClient.Builder>()
        every { BillingClient.newBuilder(any()) } returns builder
        every { builder.setListener(any()) } returns builder
        every { builder.enableAutoServiceReconnection() } returns builder
        every { builder.enablePendingPurchases(any()) } returns builder
        every { builder.build() } returns mockk(relaxed = true)
        repo = spyk(BillingRepository(mockk(relaxed = true), fake.instance))
    }

    @After
    fun tearDown() = unmockkAll()

    private fun activeSubscription(productId: String): Purchase = mockk {
        every { products } returns listOf(productId)
        every { purchaseState } returns Purchase.PurchaseState.PURCHASED
    }

    /**
     * [source] is how the coach's current tier was granted: "play" for a Google Play subscription
     * (what the sync itself records), null for App Store / Stripe / comp. [active] null = Play unreachable.
     */
    private fun tierAfterSync(
        startingTier: String,
        active: List<Purchase>?,
        source: String? = BillingRepository.PLAY_SOURCE
    ): Any? = runBlocking {
        fake.seed("coaches/c1", buildMap {
            put("subscriptionTier", startingTier)
            source?.let { put("subscriptionSource", it) }
        })
        coEvery { repo.queryActiveSubscriptions() } returns active
        repo.syncSubscriptionTierFromPlay("c1")
        fake.doc("coaches/c1")!!["subscriptionTier"]
    }

    @Test
    fun `Play product ids map to coach tiers`() {
        assertEquals(CoachTier.PRO, repo.productToTier(BillingRepository.PRODUCT_PRO))
        assertEquals(CoachTier.PLUS, repo.productToTier(BillingRepository.PRODUCT_PLUS))
        assertEquals(CoachTier.FREE, repo.productToTier("something_else"))
    }

    @Test
    fun `an active Pro subscription syncs Pro`() {
        assertEquals("pro", tierAfterSync("free", listOf(activeSubscription(BillingRepository.PRODUCT_PRO))))
    }

    @Test
    fun `a lapsed Play subscription drops the coach back to free`() {
        assertEquals("free", tierAfterSync("pro", emptyList()))
    }

    @Test
    fun `when Plus and Pro are both active the coach gets Pro`() {
        val both = listOf(
            activeSubscription(BillingRepository.PRODUCT_PLUS),
            activeSubscription(BillingRepository.PRODUCT_PRO)
        )
        assertEquals("pro", tierAfterSync("free", both))
    }

    @Test
    fun `a coach billed through the App Store or Stripe is never downgraded by an Android device`() {
        assertEquals("pro", tierAfterSync("pro", emptyList(), source = null))
    }

    @Test
    fun `a leftover Play subscription never lowers a tier paid for elsewhere`() {
        // e.g. upgraded to Pro on the web (Stripe) but still has an old Play Plus subscription
        assertEquals("pro", tierAfterSync("pro", listOf(activeSubscription(BillingRepository.PRODUCT_PLUS)), source = null))
    }

    @Test
    fun `nothing changes when Google Play can't be reached`() {
        assertEquals("pro", tierAfterSync("pro", active = null))
    }

    @Test
    fun `a Play subscription is tagged so a later lapse can be detected`() {
        tierAfterSync("free", listOf(activeSubscription(BillingRepository.PRODUCT_PLUS)), source = null)
        assertEquals(BillingRepository.PLAY_SOURCE, fake.doc("coaches/c1")!!["subscriptionSource"])
        assertEquals("plus", fake.doc("coaches/c1")!!["subscriptionTier"])
    }
}
