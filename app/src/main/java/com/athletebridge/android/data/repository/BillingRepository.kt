package com.athletebridge.android.data.repository

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.athletebridge.android.data.model.CoachTier
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        /** Marks a coach tier that was granted by a Google Play subscription. */
        const val PLAY_SOURCE = "play"
    }

    private val _purchaseState = MutableStateFlow<PurchaseResult?>(null)
    val purchaseState: StateFlow<PurchaseResult?> = _purchaseState.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enableAutoServiceReconnection()
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

    private val connectMutex = Mutex()

    /** Connect to Google Play billing service. Call before any billing operation.
     *  Serialized so overlapping callers (the app-start tier sync and the Payments screen) share one connection. */
    suspend fun connect(): Boolean = connectMutex.withLock {
        if (billingClient.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() {
                    // Lost before setup finished: report failure instead of leaving the caller suspended
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
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
        return suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { result, productDetailsResult ->
                cont.resume(
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        productDetailsResult.productDetailsList
                    } else emptyList()
                )
            }
        }
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
        return suspendCancellableCoroutine { cont ->
            billingClient.acknowledgePurchase(params) { result -> cont.resume(result) }
        }
    }

    /** Active subscriptions on this device's Play account, or null when Google Play couldn't be asked
     *  (no connection, Play Store signed out). Callers must not treat null as "no subscription". */
    suspend fun queryActiveSubscriptions(): List<Purchase>? {
        if (!connect()) return null
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        return suspendCancellableCoroutine { cont ->
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                cont.resume(
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    } else null
                )
            }
        }
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

    /**
     * Sync the coach's tier with their Google Play subscriptions; returns true when the stored tier changed.
     *
     * The highest active Play subscription wins. Tiers granted through Play are tagged
     * `subscriptionSource = "play"`, and only those are dropped back to free once Play reports no active
     * subscription — so coaches billed through the App Store or Stripe are never downgraded by an Android
     * device. Nothing changes when Play can't be reached.
     */
    suspend fun syncSubscriptionTierFromPlay(uid: String): Boolean {
        return try {
            val purchases = queryActiveSubscriptions() ?: return false
            val playTier = purchases.flatMap { it.products }
                .map { productToTier(it) }
                .maxByOrNull { it.rank } ?: CoachTier.FREE
            val ref = firestore.collection("coaches").document(uid)
            val data = ref.get().await().data ?: return false
            val currentTier = (data["subscriptionTier"] as? String)?.lowercase() ?: CoachTier.FREE.value
            val fromPlay = data["subscriptionSource"] == PLAY_SOURCE
            when {
                playTier != CoachTier.FREE -> {
                    if (fromPlay && currentTier == playTier.value) return false
                    // A tier paid for elsewhere (Stripe, App Store) is only ever raised by Play, never lowered
                    if (!fromPlay && playTier.rank <= CoachTier.fromString(currentTier).rank) return false
                    ref.update(mapOf("subscriptionTier" to playTier.value, "subscriptionSource" to PLAY_SOURCE)).await()
                    true
                }
                fromPlay && currentTier != CoachTier.FREE.value -> {
                    ref.update(mapOf("subscriptionTier" to CoachTier.FREE.value, "subscriptionSource" to FieldValue.delete())).await()
                    true
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }
}
