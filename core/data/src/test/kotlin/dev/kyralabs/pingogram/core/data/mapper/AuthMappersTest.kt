package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.EmailAddressResetState
import dev.kyralabs.pingogram.core.domain.model.PhoneNumberInfo
import dev.kyralabs.pingogram.core.domain.model.TermsOfService
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthMappersTest {
    @Test
    fun `every authorization state maps to a step`() {
        val steps =
            (listOf(AuthState.Initializing) + TdApiFixtures.allAuthorizationStates().map { AuthState.from(it)!! })
                .map { it.toAuthStep()::class.simpleName }

        assertEquals(
            listOf(
                "Initializing",
                "Initializing",
                "WaitPhoneNumber",
                "WaitPremiumPurchase",
                "WaitEmailAddress",
                "WaitEmailCode",
                "WaitCode",
                "WaitOtherDeviceConfirmation",
                "WaitRegistration",
                "WaitPassword",
                "Ready",
                "LoggingOut",
                "LoggingOut",
                "Closed",
            ),
            steps,
        )
    }

    @Test
    fun `WaitCode carries the phone number, both code types and the resend timeout`() {
        val codeInfo =
            TdApiFixtures.authenticationCodeInfo(
                phoneNumber = "+999662",
                type = TdApi.AuthenticationCodeTypeSms(5),
                nextType = TdApi.AuthenticationCodeTypeCall(5),
                timeoutSeconds = 45,
            )

        val step = AuthState.WaitCode(codeInfo).toAuthStep()

        assertEquals(AuthStep.WaitCode("+999662", AuthCodeType.Sms(5), AuthCodeType.Call(5), 45), step)
    }

    @Test
    fun `WaitCode without a next type`() {
        val step = AuthState.WaitCode(TdApiFixtures.authenticationCodeInfo(nextType = null)).toAuthStep()

        assertEquals(null, (step as AuthStep.WaitCode).nextType)
    }

    @Test
    fun `every code type is mapped`() {
        val types =
            listOf(
                TdApi.AuthenticationCodeTypeTelegramMessage(5),
                TdApi.AuthenticationCodeTypeSms(6),
                TdApi.AuthenticationCodeTypeSmsWord("a"),
                TdApi.AuthenticationCodeTypeSmsPhrase("apple"),
                TdApi.AuthenticationCodeTypeCall(4),
                TdApi.AuthenticationCodeTypeFlashCall("+44*"),
                TdApi.AuthenticationCodeTypeMissedCall("+44 20", 4),
                TdApi.AuthenticationCodeTypeFragment("https://fragment.com/number", 5),
                TdApi.AuthenticationCodeTypeFirebaseAndroid(null, 6),
                TdApi.AuthenticationCodeTypeFirebaseIos("receipt", 30, 6),
            ).map { it.toAuthCodeType() }

        assertEquals(
            listOf(
                AuthCodeType.TelegramMessage(5),
                AuthCodeType.Sms(6),
                AuthCodeType.SmsWord("a"),
                AuthCodeType.SmsPhrase("apple"),
                AuthCodeType.Call(4),
                AuthCodeType.FlashCall("+44*"),
                AuthCodeType.MissedCall("+44 20", 4),
                AuthCodeType.Fragment("https://fragment.com/number", 5),
                AuthCodeType.Firebase(6),
                AuthCodeType.Firebase(6),
            ),
            types,
        )
    }

    @Test
    fun `WaitPassword carries the hint and recovery details`() {
        val step = AuthState.from(TdApiFixtures.waitPassword("cat", true, false, "a***@example.org"))!!.toAuthStep()

        assertEquals(AuthStep.WaitPassword("cat", true, "a***@example.org"), step)
    }

    @Test
    fun `WaitRegistration carries the terms of service`() {
        val terms = TdApi.TermsOfService(TdApiFixtures.formattedText("Be nice"), 16, true)

        val step = AuthState.WaitRegistration(terms).toAuthStep()

        assertEquals(AuthStep.WaitRegistration(TermsOfService("Be nice", 16, mustShowPopup = true)), step)
    }

    @Test
    fun `WaitEmailCode carries the pattern, the code length and the reset state`() {
        val states =
            listOf(null, TdApi.EmailAddressResetStateAvailable(3600), TdApi.EmailAddressResetStatePending(120))
                .map { reset ->
                    AuthState
                        .from(TdApiFixtures.waitEmailCode("a***@example.org", 6, emailAddressResetState = reset))!!
                        .toAuthStep()
                }

        assertEquals(
            listOf(
                AuthStep.WaitEmailCode("a***@example.org", 6, null),
                AuthStep.WaitEmailCode("a***@example.org", 6, EmailAddressResetState.Available(3600)),
                AuthStep.WaitEmailCode("a***@example.org", 6, EmailAddressResetState.Pending(120)),
            ),
            states,
        )
    }

    @Test
    fun `WaitPremiumPurchase keeps only the support contact`() {
        val step = AuthState.from(TdApiFixtures.waitPremiumPurchase())!!.toAuthStep()

        assertEquals(AuthStep.WaitPremiumPurchase("support@example.org", "Login"), step)
    }

    @Test
    fun `a country takes TDLib's flag, or derives it from the ISO code`() {
        val countries =
            listOf(
                TdApi.CountryInfo("GB", "United Kingdom", "United Kingdom", "🇬🇧", false, arrayOf("44")),
                TdApi.CountryInfo("DE", "Deutschland", "Germany", "", true, arrayOf("49")),
            ).map { it.toCountry() }

        assertEquals(
            listOf(
                Country("GB", "United Kingdom", "United Kingdom", "🇬🇧", listOf("44"), false),
                Country("DE", "Deutschland", "Germany", "🇩🇪", listOf("49"), true),
            ),
            countries,
        )
    }

    @Test
    fun `phone number info maps with and without a country`() {
        val known =
            TdApi.PhoneNumberInfo(
                TdApi.CountryInfo("GB", "United Kingdom", "United Kingdom", "", false, arrayOf("44")),
                "44",
                "7911 12- ---",
                false,
            )
        val unknown = TdApi.PhoneNumberInfo(null, "", "123", true)

        assertEquals("GB", known.toPhoneNumberInfo().country?.isoCode)
        assertEquals("7911 12- ---", known.toPhoneNumberInfo().formattedNumber)
        assertEquals(PhoneNumberInfo(null, "", "123", isAnonymous = true), unknown.toPhoneNumberInfo())
    }

    @Test
    fun `every connection state is mapped`() {
        assertEquals(ConnectionStatus.entries, ConnectionState.entries.map { it.toConnectionStatus() })
    }
}
