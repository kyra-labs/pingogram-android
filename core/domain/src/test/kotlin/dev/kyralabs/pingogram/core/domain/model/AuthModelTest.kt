package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthModelTest {
    @Test
    fun `flagEmoji turns an ISO code into regional indicator symbols`() {
        assertEquals("🇬🇧", flagEmoji("GB"))
        assertEquals("🇩🇪", flagEmoji("de"))
    }

    @Test
    fun `flagEmoji is empty for anything but two letters`() {
        assertEquals(listOf("", "", "", ""), listOf("", "G", "GBR", "4G").map(::flagEmoji))
    }

    @Test
    fun `codeLength is known for digit codes and unknown for words, phrases and flash calls`() {
        val lengths =
            listOf(
                AuthCodeType.TelegramMessage(5),
                AuthCodeType.Sms(6),
                AuthCodeType.Call(4),
                AuthCodeType.MissedCall("+44 20", 4),
                AuthCodeType.Fragment("https://fragment.com", 5),
                AuthCodeType.Firebase(6),
                AuthCodeType.SmsWord("a"),
                AuthCodeType.SmsPhrase("apple"),
                AuthCodeType.FlashCall("+44*"),
            ).map { it.codeLength }

        assertEquals(listOf(5, 6, 4, 4, 5, 6, null, null, null), lengths)
    }

    @Test
    fun `a zero code length counts as unknown`() {
        assertNull(AuthCodeType.Sms(0).codeLength)
    }

    @Test
    fun `only words and phrases are not numeric`() {
        assertFalse(AuthCodeType.SmsWord("a").isNumeric)
        assertFalse(AuthCodeType.SmsPhrase("apple").isNumeric)
        assertTrue(AuthCodeType.Sms(5).isNumeric)
    }

    @Test
    fun `toString leaves out phone numbers, links, hints and email patterns`() {
        val printed =
            listOf(
                AuthStep.WaitCode("+999662", AuthCodeType.Sms(5), null, 60),
                AuthStep.WaitOtherDeviceConfirmation("tg://login?token=secret"),
                AuthStep.WaitPassword("my dog", true, "a***@example.org"),
                AuthStep.WaitEmailCode("b***@example.org", 6, null),
                PhoneNumberInfo(null, "999", "66 2", isAnonymous = false),
            ).joinToString()

        assertFalse(listOf("99966", "token", "dog", "example.org", "66 2").any { it in printed }, printed)
    }
}
