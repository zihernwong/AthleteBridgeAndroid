package com.athletebridge.android.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.athletebridge.android.data.model.CoachTier
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore
) : PurchasesUpdatedListener {

    companion object {
        // Product IDs must match what you configure in Google Play Console
        const val PRODUCT_PLUS = "coach_plus_monthly"
        const val PRODUCT_PRO = "coach_pro_monthly"
    }

    private val _purchaseState = MutableStateFlow<PurchaseResult?>(null)
    val purchaseState: StateFlow<PurchaseResult?> = _purchaseState.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    sealed class PurchaseResult {
        data class Success(val productId: String, val purchaseToken: String) : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
        object Cancelled : PurchaseResult()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null -> {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseState.value = PurchaseResult.Cancelled
            }
            else -> {
                _purchaseState.value = PurchaseResult.Error(result.debugMessage)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val productId = purchase.products.firstOrNull() ?: return
            _purchaseState.value = PurchaseResult.Success(productId, purchase.purchaseToken)
        }
    }

    /** Connect to Google Play billing service. Call before any billing operation. */
    suspend fun connect(): Boolean = suspendCancellableCoroutine { cont ->
        if (billingClient.isReady) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            override fun onBillingServiceDisconnected() {
                // Will retry on next call
            }
        })
    }

    /** Query available subscription products from Google Play. */
    suspend fun queryProducts(): List<ProductDetails> {
        if (!connect()) return emptyList()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(PRODUCT_PLUS, PRODUCT_PRO).map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()
        val result = billingClient.queryProductDetails(params)
        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            result.productDetailsList ?: emptyList()
        } else emptyList()
    }

    /** Launch the Google Play subscription purchase flow. */
    suspend fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails): BillingResult {
        if (!connect()) return BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
            .build()

        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        return billingClient.launchBillingFlow(activity, params)
    }

    /** Acknowledge a purchase (required within 3 days or Google refunds it). */
    suspend fun acknowledgePurchase(purchaseToken: String): BillingResult {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        return billingClient.acknowledgePurchase(params)
    }

    /** Query active subscriptions the user already has. */
    suspend fun queryActiveSubscriptions(): List<Purchase> {
        if (!connect()) return emptyList()
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        return if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        } else emptyList()
    }

    fun clearPurchaseState() {
        _purchaseState.value = null
    }

    /** Map a Google Play product ID to a CoachTier. */
    fun productToTier(productId: String): CoachTier = when (productId) {
        PRODUCT_PRO -> CoachTier.PRO
        PRODUCT_PLUS -> CoachTier.PLUS
        else -> CoachTier.FREE
    }

    /** Sync the coach's subscription tier in Firestore based on active Google Play subscriptions. */
    suspend fun syncSubscriptionTierFromPlay(uid: String) {
        try {
            val purchases = queryActiveSubscriptions()
            val productId = purchases.firstOrNull()?.products?.firstOrNull() ?: return
            val tier = productToTier(productId)
            firestore.collection("coaches").document(uid)
                .update("subscriptionTier", tier.value).await()
        } catch (_: Exception) {}
    }
}
