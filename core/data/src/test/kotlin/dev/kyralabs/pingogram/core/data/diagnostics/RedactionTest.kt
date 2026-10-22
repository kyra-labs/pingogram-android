package dev.kyralabs.pingogram.core.data.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** What the feedback report takes out of crashes and TDLib's log lines before the user sees them. */
class RedactionTest {
    @Test
    fun `takes out the values of TDLib fields that hold text or names`() {
        assertEquals(
            "inputMessageText { text = [redacted] } first_name = [redacted] phone_number = [redacted]",
            Redaction.redact("inputMessageText { text = \"hello\" } first_name = Ada phone_number = 447911123456"),
        )
    }

    @Test
    fun `takes out quoted text, emails and tokens`() {
        assertEquals(
            "Can't parse \"[text]\" for [email] with [token]",
            Redaction.redact("Can't parse \"secret plan\" for ada@example.org with " + "a".repeat(40)),
        )
    }

    @Test
    fun `takes out phone numbers and long ids, grouped or not`() {
        assertEquals(
            "call [number] or [number], chat [number]",
            Redaction.redact("call +44 7911 123456 or 07911123456, chat -1001234567890"),
        )
    }

    @Test
    fun `keeps TDLib's timestamps, file lines and short numbers`() {
        val line = "[ 1][t 4][1729601234.567890][Td.cpp:3141][#2][!Td]\tFailed to open file of size 4096: error 13"

        assertEquals(line, Redaction.redact(line))
    }
}
