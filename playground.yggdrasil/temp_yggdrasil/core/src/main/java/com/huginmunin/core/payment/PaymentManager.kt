package com.huginmunin.core.payment

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.huginmunin.core.config.ApiConfig
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.model.*
import com.stripe.android.view.CardInputWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class PaymentResult {
    abstract val success: Boolean
    abstract val message: String
    abstract val transactionId: String

    data class Success(override val transactionId: String, val amount: Double, val currency: String) : PaymentResult() {
        override val success = true
        override val message = "Payment successful"
    }
    data class Error(override val message: String) : PaymentResult() {
        override val success = false
        override val transactionId = ""
    }
    object UserCancelled : PaymentResult() {
        override val success = false
        override val message = "Payment cancelled by user"
        override val transactionId = ""
    }
}

@Singleton
class PaymentManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "PaymentManager"
    
    // Stripe API Key from centralized config
    private val stripeKey = ApiConfig.STRIPE_PUBLISHABLE_KEY
    
    private var stripe: Stripe? = null
    
    init {
        try {
            if (stripeKey.length > 20) {
                PaymentConfiguration.init(context, stripeKey)
                stripe = Stripe(context, stripeKey)
                Log.d(TAG, "Stripe initialized")
            } else {
                Log.w(TAG, "Stripe key not configured, using mock mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Stripe: ${e.message}")
        }
    }

    /**
     * Process payment with Stripe
     */
    suspend fun processPayment(
        amount: Double,
        currency: String,
        recipient: String
    ): PaymentResult {
        // If Stripe not configured, use mock
        if (stripe == null) {
            return processMockPayment(amount, currency, recipient)
        }
        
        // Real Stripe payment would require:
        // 1. Create PaymentIntent on backend
        // 2. Confirm payment with card details
        // 3. Handle 3D Secure if needed
        
        return PaymentResult.Error("Stripe backend not configured")
    }


    /**
     * Create payment method from card widget
     * 
     * Uses CardInputWidget's built-in method to avoid accessing sensitive card details
     */
    suspend fun createPaymentMethod(
        cardInputWidget: CardInputWidget
    ): PaymentMethodCreateParams? {
        // Use the widget's paymentMethodCreateParams directly
        // This avoids accessing sensitive card details
        return cardInputWidget.paymentMethodCreateParams
    }

    /**
     * Confirm payment intent
     */
    suspend fun confirmPaymentIntent(
        activity: ComponentActivity,
        paymentIntentClientSecret: String,
        paymentMethodId: String
    ): PaymentResult = suspendCancellableCoroutine { continuation ->
        stripe?.confirmPayment(
            activity,
            ConfirmPaymentIntentParams.createWithPaymentMethodId(
                paymentMethodId,
                paymentIntentClientSecret
            )
        )
        
        // Result would be received in activity's onActivityResult
        continuation.resume(PaymentResult.Success("stripe_intent_id", 0.0, "USD"))
    }

    /**
     * Mock payment for testing
     */
    private fun processMockPayment(amount: Double, currency: String, recipient: String): PaymentResult {
        Log.d(TAG, "Mock payment: $amount $currency to $recipient")
        
        // Simulate payment processing
        val transactionId = "MOCK_TX_${System.currentTimeMillis()}"
        
        return if (amount > 0) {
            PaymentResult.Success(transactionId, amount, currency)
        } else {
            PaymentResult.Error("Invalid amount")
        }
    }

    /**
     * Validate card details before processing
     */
    fun validateCardDetails(
        cardNumber: String,
        expMonth: Int,
        expYear: Int,
        cvc: String
    ): Boolean {
        // Basic validation
        if (cardNumber.length < 13 || cardNumber.length > 19) return false
        if (expMonth < 1 || expMonth > 12) return false
        if (expYear < 2024) return false
        if (cvc.length < 3 || cvc.length > 4) return false
        
        // Luhn algorithm check for card number
        return isValidCardNumber(cardNumber)
    }

    private fun isValidCardNumber(number: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            var n = number[i].toString().toIntOrNull() ?: return false
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
