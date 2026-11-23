package com.athletebridge.android.ui.payments

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.repository.BillingRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PaymentsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val bookingRepo = mockk<BookingRepository>()
    private val coachRepo = mockk<CoachRepository>()
    private val billing = mockk<BillingRepository>(relaxed = true)
    private val activity = mockk<Activity>(relaxed = true)

    private fun vm() = PaymentsViewModel(bookingRepo, mockk(relaxed = true), coachRepo, mockk(relaxed = true), billing)

    private fun b(id: String, status: String?, payment: String? = null) =
        BookingItem(id = id, status = status, paymentStatus = payment, rateUSD = 50.0)

    @Test
    fun `client sees confirmed sessions split into paid and unpaid`() {
        every { bookingRepo.observeClientBookings("u1") } returns flowOf(
            listOf(b("paid", "confirmed", "paid"), b("due", "Confirmed"), b("cancelled", "cancelled"), b("requested", "requested"))
        )
        val vm = vm().also { it.loadClientBookings("u1") }
        assertEquals(listOf("paid"), vm.uiState.value.paidBookings.map { it.id })
        assertEquals(listOf("due"), vm.uiState.value.unpaidBookings.map { it.id })
    }

    @Test
    fun `coach's unpaid tab leaves out cancelled, rejected and declined sessions`() {
        every { bookingRepo.observeCoachBookings("c1") } returns flowOf(
            listOf(
                b("paid", "confirmed", "paid"), b("due", "confirmed"),
                b("cancelled", "cancelled"), b("rejected", "rejected"), b("declined", "declined_by_client")
            )
        )
        val vm = vm().also { it.loadCoachBookings("c1") }
        assertEquals(listOf("paid"), vm.uiState.value.paidBookings.map { it.id })
        assertEquals(listOf("due"), vm.uiState.value.unpaidBookings.map { it.id })
    }

    @Test
    fun `saving a payment handle keeps the other handles`() {
        coEvery { coachRepo.updatePayments(any(), any()) } returns Result.success(Unit)
        val vm = vm()
        vm.savePaymentHandle("c1", "zelle", "612-555-0100", mapOf("venmo" to "@mei"))
        coVerify { coachRepo.updatePayments("c1", mapOf("venmo" to "@mei", "zelle" to "612-555-0100")) }
        assertEquals("Payment handle saved.", vm.uiState.value.infoMessage)
    }

    @Test
    fun `subscribing explains when Play can't load the product`() {
        coEvery { billing.queryProducts() } returns emptyList()
        val vm = vm()
        vm.subscribeTo(activity, BillingRepository.PRODUCT_PRO)
        assertTrue(vm.uiState.value.errorMessage!!.contains("couldn't load this subscription"))
        assertEquals(false, vm.uiState.value.isLoadingProducts)
    }

    @Test
    fun `subscribing explains billing errors in plain language`() {
        val product = mockk<ProductDetails> { every { productId } returns BillingRepository.PRODUCT_PRO }
        coEvery { billing.queryProducts() } returns listOf(product)
        coEvery { billing.launchPurchaseFlow(any(), any()) } returns mockk<BillingResult> {
            every { responseCode } returns BillingClient.BillingResponseCode.BILLING_UNAVAILABLE
        }
        val vm = vm()
        vm.subscribeTo(activity, BillingRepository.PRODUCT_PRO)
        assertTrue(vm.uiState.value.errorMessage!!.startsWith("Google Play billing isn't available"))
    }

    @Test
    fun `a completed purchase is acknowledged and synced to the coach tier`() {
        every { billing.purchaseState } returns MutableStateFlow(BillingRepository.PurchaseResult.Success(BillingRepository.PRODUCT_PRO, "tok"))
        val vm = vm()
        vm.handlePlayPurchaseResult("c1")
        coVerify { billing.acknowledgePurchase("tok") }
        coVerify { billing.syncSubscriptionTierFromPlay("c1") }
        assertTrue(vm.uiState.value.purchaseSuccess)
    }

    @Test
    fun `a failed purchase surfaces Play's message`() {
        every { billing.purchaseState } returns MutableStateFlow(BillingRepository.PurchaseResult.Error("Card declined"))
        val vm = vm()
        vm.handlePlayPurchaseResult("c1")
        assertEquals("Card declined", vm.uiState.value.errorMessage)
        coVerify(exactly = 0) { billing.syncSubscriptionTierFromPlay(any()) }
    }
}
