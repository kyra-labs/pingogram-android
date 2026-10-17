package dev.kyralabs.pingogram.core.tdlib

import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StateMappingTest {
    @Test
    fun `every authorization state of this TDLib version is mapped`() {
        val mapped = TdApiFixtures.allAuthorizationStates().map { AuthState.from(it)?.javaClass?.simpleName }

        assertEquals(
            listOf(
                "WaitTdlibParameters",
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
                "Closing",
                "Closed",
            ),
            mapped,
        )
    }

    @Test
    fun `WaitCode carries TDLib's code info`() {
        val codeInfo = TdApiFixtures.authenticationCodeInfo()

        val state = AuthState.from(TdApiFixtures.waitCode(codeInfo)) as AuthState.WaitCode

        assertTrue(state.codeInfo === codeInfo)
    }

    @Test
    fun `WaitPassword copies the hint and recovery details`() {
        val state = AuthState.from(TdApiFixtures.waitPassword("cat", true, false, "a***@example.org"))

        assertEquals(AuthState.WaitPassword("cat", true, false, "a***@example.org"), state)
    }

    @Test
    fun `toString leaves out phone numbers, login links and password hints`() {
        val printed =
            listOf(
                TdApiFixtures.waitCode(),
                TdApiFixtures.waitOtherDeviceConfirmation("tg://login?token=secret"),
                TdApiFixtures.waitPassword(passwordHint = "my dog's name"),
                TdApiFixtures.waitEmailCode(emailAddressPattern = "a***@example.org"),
                TdApiFixtures.waitRegistration(),
            ).joinToString { AuthState.from(it).toString() }

        assertFalse(listOf("99966", "token", "dog", "example.org").any { it in printed }, printed)
    }

    @Test
    fun `an authorization state unknown to this TDLib version maps to null`() {
        val unknown =
            object : TdApi.AuthorizationState() {
                override fun getConstructor() = 0
            }

        assertNull(AuthState.from(unknown))
    }

    @Test
    fun `every connection state is mapped`() {
        val mapped = TdApiFixtures.allConnectionStates().map(ConnectionState::from)

        assertEquals(ConnectionState.entries, mapped)
    }
}
