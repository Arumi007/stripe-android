package com.stripe.android.link.repositories

import com.google.common.truth.Truth.assertThat
import com.stripe.android.core.networking.ApiRequest
import com.stripe.android.link.LinkPaymentDetails
import com.stripe.android.link.model.PaymentDetailsFixtures
import com.stripe.android.model.ConsumerPaymentDetails
import com.stripe.android.model.ConsumerPaymentDetailsCreateParams
import com.stripe.android.model.ConsumerSession
import com.stripe.android.model.ConsumerSessionLookup
import com.stripe.android.model.ConsumerSessionSignup
import com.stripe.android.model.ConsumerSignUpConsentAction
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.model.VerificationType
import com.stripe.android.networking.StripeRepository
import com.stripe.android.payments.core.analytics.ErrorReporter
import com.stripe.android.repository.ConsumersApiService
import com.stripe.android.testing.FakeErrorReporter
import com.stripe.android.ui.core.FieldValuesToParamsMapConverter
import com.stripe.android.uicore.elements.IdentifierSpec
import com.stripe.android.uicore.forms.FormFieldEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@Suppress("LargeClass")
@RunWith(RobolectricTestRunner::class)
class LinkApiRepositoryTest {
    private val stripeRepository = mock<StripeRepository>()
    private val consumersApiService = mock<ConsumersApiService>()
    private val errorReporter = FakeErrorReporter()

    private val paymentIntent = mock<PaymentIntent>().apply {
        whenever(clientSecret).thenReturn("secret")
    }

    private val linkRepository = LinkApiRepository(
        publishableKeyProvider = { PUBLISHABLE_KEY },
        stripeAccountIdProvider = { STRIPE_ACCOUNT_ID },
        stripeRepository = stripeRepository,
        consumersApiService = consumersApiService,
        workContext = Dispatchers.IO,
        locale = Locale.US,
        errorReporter = errorReporter
    )

    @Before
    fun clearErrorReporter() {
        errorReporter.clear()
    }

    @Test
    fun `lookupConsumer sends correct parameters`() = runTest {
        val email = "email@example.com"
        linkRepository.lookupConsumer(email)

        verify(consumersApiService).lookupConsumerSession(
            eq(email),
            eq(CONSUMER_SURFACE),
            eq(ApiRequest.Options(PUBLISHABLE_KEY, STRIPE_ACCOUNT_ID))
        )
    }

    @Test
    fun `lookupConsumer returns successful result`() = runTest {
        val consumerSessionLookup = mock<ConsumerSessionLookup>()
        whenever(
            consumersApiService.lookupConsumerSession(
                any(),
                any(),
                any(),
            )
        )
            .thenReturn(consumerSessionLookup)

        val result = linkRepository.lookupConsumer("email")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(consumerSessionLookup)
    }

    @Test
    fun `lookupConsumer catches exception and returns failure`() = runTest {
        whenever(
            consumersApiService.lookupConsumerSession(
                any(),
                any(),
                any(),
            )
        )
            .thenThrow(RuntimeException("error"))

        val result = linkRepository.lookupConsumer("email")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `consumerSignUp sends correct parameters`() = runTest {
        val email = "email@example.com"
        val phone = "phone"
        val country = "US"
        val name = "name"
        linkRepository.consumerSignUp(
            email,
            phone,
            country,
            name,
            ConsumerSignUpConsentAction.Checkbox
        )

        verify(consumersApiService).signUp(
            email = email,
            phoneNumber = phone,
            country = country,
            name = name,
            locale = Locale.US,
            amount = null,
            currency = null,
            paymentIntentId = null,
            setupIntentId = null,
            requestSurface = "android_payment_element",
            consentAction = ConsumerSignUpConsentAction.Checkbox,
            requestOptions = ApiRequest.Options(PUBLISHABLE_KEY, STRIPE_ACCOUNT_ID),
        )
    }

    @Test
    fun `consumerSignUp returns successful result`() = runTest {
        val consumerSession = mock<ConsumerSessionSignup>()
        whenever(
            consumersApiService.signUp(
                email = any(),
                phoneNumber = any(),
                country = any(),
                name = anyOrNull(),
                locale = anyOrNull(),
                amount = anyOrNull(),
                currency = anyOrNull(),
                paymentIntentId = anyOrNull(),
                setupIntentId = anyOrNull(),
                requestSurface = any(),
                consentAction = any(),
                requestOptions = any()
            )
        ).thenReturn(Result.success(consumerSession))

        val result = linkRepository.consumerSignUp(
            "email",
            "phone",
            "country",
            "name",
            ConsumerSignUpConsentAction.Checkbox
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(consumerSession)
    }

    @Test
    fun `consumerSignUp catches exception and returns failure`() = runTest {
        whenever(
            consumersApiService.signUp(
                email = any(),
                phoneNumber = any(),
                country = any(),
                name = anyOrNull(),
                locale = anyOrNull(),
                amount = anyOrNull(),
                currency = anyOrNull(),
                paymentIntentId = anyOrNull(),
                setupIntentId = anyOrNull(),
                requestSurface = any(),
                consentAction = any(),
                requestOptions = any()
            )
        ).thenReturn(Result.failure(RuntimeException("error")))

        val result = linkRepository.consumerSignUp(
            "email",
            "phone",
            "country",
            "name",
            ConsumerSignUpConsentAction.Implied
        )

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `createPaymentDetails for card sends correct parameters`() = runTest {
        val secret = "secret"
        val email = "email@stripe.com"
        val consumerKey = "key"

        linkRepository.createCardPaymentDetails(
            paymentMethodCreateParams = cardPaymentMethodCreateParams,
            userEmail = email,
            stripeIntent = paymentIntent,
            consumerSessionClientSecret = secret,
            consumerPublishableKey = consumerKey,
            active = false,
        )

        verify(consumersApiService).createPaymentDetails(
            eq(secret),
            argThat<ConsumerPaymentDetailsCreateParams> {
                toParamMap() == mapOf(
                    "type" to "card",
                    "billing_email_address" to "email@stripe.com",
                    "card" to mapOf(
                        "number" to "5555555555554444",
                        "exp_month" to "12",
                        "exp_year" to "2050"
                    ),
                    "billing_address" to mapOf(
                        "country_code" to "US",
                        "postal_code" to "12345"
                    ),
                    "active" to false,
                )
            },
            requestSurface = eq("android_payment_element"),
            requestOptions = eq(ApiRequest.Options(consumerKey)),
        )
    }

    @Test
    fun `createPaymentDetails for card without consumerPublishableKey sends correct parameters`() =
        runTest {
            val secret = "secret"
            val email = "email@stripe.com"

            linkRepository.createCardPaymentDetails(
                paymentMethodCreateParams = cardPaymentMethodCreateParams,
                userEmail = email,
                stripeIntent = paymentIntent,
                consumerSessionClientSecret = secret,
                consumerPublishableKey = null,
                active = false,
            )

            verify(consumersApiService).createPaymentDetails(
                consumerSessionClientSecret = eq(secret),
                paymentDetailsCreateParams = argThat<ConsumerPaymentDetailsCreateParams> {
                    toParamMap() == mapOf(
                        "type" to "card",
                        "billing_email_address" to "email@stripe.com",
                        "card" to mapOf(
                            "number" to "5555555555554444",
                            "exp_month" to "12",
                            "exp_year" to "2050"
                        ),
                        "billing_address" to mapOf(
                            "country_code" to "US",
                            "postal_code" to "12345"
                        ),
                        "active" to false,
                    )
                },
                requestSurface = eq("android_payment_element"),
                requestOptions = eq(ApiRequest.Options(PUBLISHABLE_KEY, STRIPE_ACCOUNT_ID)),
            )
        }

    @Test
    fun `createPaymentDetails for card returns new LinkPaymentDetails when successful`() = runTest {
        val consumerSessionSecret = "consumer_session_secret"
        val email = "email@stripe.com"
        val paymentDetails = PaymentDetailsFixtures.CONSUMER_SINGLE_PAYMENT_DETAILS
        whenever(
            consumersApiService.createPaymentDetails(
                consumerSessionClientSecret = any(),
                paymentDetailsCreateParams = any(),
                requestSurface = any(),
                requestOptions = any(),
            )
        ).thenReturn(Result.success(paymentDetails))

        val result = linkRepository.createCardPaymentDetails(
            paymentMethodCreateParams = cardPaymentMethodCreateParams,
            userEmail = email,
            stripeIntent = paymentIntent,
            consumerSessionClientSecret = consumerSessionSecret,
            consumerPublishableKey = null,
            active = false,
        )

        assertThat(result.isSuccess).isTrue()

        val newLinkPaymentDetails = result.getOrThrow()

        assertThat(newLinkPaymentDetails.paymentDetails)
            .isEqualTo(paymentDetails.paymentDetails.first())
        assertThat(newLinkPaymentDetails.paymentMethodCreateParams)
            .isEqualTo(
                PaymentMethodCreateParams.createLink(
                    paymentDetails.paymentDetails.first().id,
                    consumerSessionSecret,
                    mapOf("card" to mapOf("cvc" to "123"))
                )
            )
        assertThat(newLinkPaymentDetails.buildFormValues()).isEqualTo(
            mapOf(
                IdentifierSpec.get("type") to "card",
                IdentifierSpec.CardNumber to "5555555555554444",
                IdentifierSpec.CardCvc to "123",
                IdentifierSpec.CardExpMonth to "12",
                IdentifierSpec.CardExpYear to "2050",
                IdentifierSpec.Country to "US",
                IdentifierSpec.PostalCode to "12345"
            )
        )
    }

    @Test
    fun `createPaymentDetails for card catches exception and returns failure`() = runTest {
        whenever(
            consumersApiService.createPaymentDetails(
                consumerSessionClientSecret = any(),
                paymentDetailsCreateParams = any(),
                requestSurface = any(),
                requestOptions = any(),
            )
        ).thenReturn(Result.failure(RuntimeException("error")))

        val result = linkRepository.createCardPaymentDetails(
            paymentMethodCreateParams = cardPaymentMethodCreateParams,
            userEmail = "email@stripe.com",
            stripeIntent = paymentIntent,
            consumerSessionClientSecret = "secret",
            consumerPublishableKey = null,
            active = false,
        )
        val loggedErrors = errorReporter.getLoggedErrors()

        assertThat(result.isFailure).isTrue()
        assertThat(loggedErrors.size).isEqualTo(1)
        assertThat(loggedErrors.first()).isEqualTo(ErrorReporter.ExpectedErrorEvent.LINK_CREATE_CARD_FAILURE.eventName)
    }

    @Test
    fun `shareCardPaymentDetails returns LinkPaymentDetails_Saved`() = runTest {
        val consumerSessionSecret = "consumer_session_secret"
        val id = "csmrpd*AYq4D_sXdAAAAOQ0"

        whenever(
            stripeRepository.sharePaymentDetails(
                consumerSessionClientSecret = any(),
                id = any(),
                extraParams = anyOrNull(),
                requestOptions = any(),
            )
        ).thenReturn(Result.success("pm_123"))

        val result = linkRepository.shareCardPaymentDetails(
            paymentMethodCreateParams = cardPaymentMethodCreateParams,
            consumerSessionClientSecret = consumerSessionSecret,
            id = id,
            last4 = "4242",
        )

        assertThat(result.isSuccess).isTrue()
        val savedLinkPaymentDetails = result.getOrThrow() as LinkPaymentDetails.Saved

        verify(stripeRepository).sharePaymentDetails(
            consumerSessionClientSecret = consumerSessionSecret,
            id = id,
            extraParams = mapOf("payment_method_options" to mapOf("card" to mapOf("cvc" to "123"))),
            requestOptions = ApiRequest.Options(apiKey = PUBLISHABLE_KEY, stripeAccount = STRIPE_ACCOUNT_ID)
        )
        assertThat(savedLinkPaymentDetails.paymentDetails)
            .isEqualTo(ConsumerPaymentDetails.Passthrough(id = "pm_123", last4 = "4242"))
        assertThat(savedLinkPaymentDetails.paymentMethodCreateParams)
            .isEqualTo(
                PaymentMethodCreateParams.createLink(
                    "pm_123",
                    consumerSessionSecret,
                    mapOf("card" to mapOf("cvc" to "123"))
                )
            )
    }

    @Test
    fun `when shareCardPaymentDetails fails, an error is reported`() = runTest {
        val consumerSessionSecret = "consumer_session_secret"

        whenever(
            stripeRepository.sharePaymentDetails(
                consumerSessionClientSecret = any(),
                id = any(),
                extraParams = anyOrNull(),
                requestOptions = any(),
            )
        ).thenReturn(Result.failure(RuntimeException("error")))

        val result = linkRepository.shareCardPaymentDetails(
            paymentMethodCreateParams = cardPaymentMethodCreateParams,
            consumerSessionClientSecret = consumerSessionSecret,
            id = "csmrpd*AYq4D_sXdAAAAOQ0",
            last4 = "4242",
        )
        val loggedErrors = errorReporter.getLoggedErrors()

        assertThat(result.isFailure).isTrue()
        assertThat(loggedErrors.size).isEqualTo(1)
        assertThat(loggedErrors.first()).isEqualTo(ErrorReporter.ExpectedErrorEvent.LINK_SHARE_CARD_FAILURE.eventName)
    }

    @Test
    fun `startVerification sends correct parameters`() = runTest {
        val secret = "secret"
        val consumerKey = "key"
        linkRepository.startVerification(secret, consumerKey)

        verify(consumersApiService).startConsumerVerification(
            consumerSessionClientSecret = secret,
            locale = Locale.US,
            requestSurface = CONSUMER_SURFACE,
            type = VerificationType.SMS,
            customEmailType = null,
            connectionsMerchantName = null,
            requestOptions = ApiRequest.Options(consumerKey),
        )
    }

    @Test
    fun `startVerification without consumerPublishableKey sends correct parameters`() = runTest {
        val secret = "secret"
        linkRepository.startVerification(secret, null)

        verify(consumersApiService).startConsumerVerification(
            consumerSessionClientSecret = secret,
            locale = Locale.US,
            requestSurface = CONSUMER_SURFACE,
            type = VerificationType.SMS,
            customEmailType = null,
            connectionsMerchantName = null,
            requestOptions = ApiRequest.Options(PUBLISHABLE_KEY, STRIPE_ACCOUNT_ID),
        )
    }

    @Test
    fun `startVerification returns successful result`() = runTest {
        val consumerSession = mock<ConsumerSession>()
        whenever(
            consumersApiService.startConsumerVerification(
                consumerSessionClientSecret = any(),
                locale = any(),
                requestSurface = any(),
                type = any(),
                customEmailType = anyOrNull(),
                connectionsMerchantName = anyOrNull(),
                requestOptions = any(),
            )
        )
            .thenReturn(consumerSession)

        val result = linkRepository.startVerification("secret", "key")

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(consumerSession)
    }

    @Test
    fun `startVerification catches exception and returns failure`() = runTest {
        whenever(
            consumersApiService.startConsumerVerification(
                consumerSessionClientSecret = any(),
                locale = any(),
                requestSurface = any(),
                type = any(),
                customEmailType = anyOrNull(),
                connectionsMerchantName = anyOrNull(),
                requestOptions = any(),
            )
        )
            .thenThrow(RuntimeException("error"))

        val result = linkRepository.startVerification("secret", "key")

        assertThat(result.isFailure).isTrue()
    }

    private val cardPaymentMethodCreateParams =
        FieldValuesToParamsMapConverter.transformToPaymentMethodCreateParams(
            mapOf(
                IdentifierSpec.CardNumber to FormFieldEntry("5555555555554444", true),
                IdentifierSpec.CardCvc to FormFieldEntry("123", true),
                IdentifierSpec.CardExpMonth to FormFieldEntry("12", true),
                IdentifierSpec.CardExpYear to FormFieldEntry("2050", true),
                IdentifierSpec.Country to FormFieldEntry("US", true),
                IdentifierSpec.PostalCode to FormFieldEntry("12345", true)
            ),
            "card",
            false
        )

    companion object {
        const val PUBLISHABLE_KEY = "publishableKey"
        const val STRIPE_ACCOUNT_ID = "stripeAccountId"
        const val CONSUMER_SURFACE = "android_payment_element"
    }
}
