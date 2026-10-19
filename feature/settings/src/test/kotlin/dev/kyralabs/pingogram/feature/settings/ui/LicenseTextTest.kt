package dev.kyralabs.pingogram.feature.settings.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LicenseTextTest {
    @Test
    fun `joins the lines of a paragraph and keeps paragraphs apart`() {
        val text =
            "  Apache License\n  Version 2.0\n\n  1. Definitions.\n\n" +
                "     \"License\" shall mean\n     the terms.\n"

        assertEquals(
            "Apache License Version 2.0\n\n1. Definitions.\n\n\"License\" shall mean the terms.",
            reflowLicenseText(text),
        )
    }

    @Test
    fun `separator lines break paragraphs and are left out`() {
        val text = "Preamble.\n-------------\nSIL OPEN FONT LICENSE\n-------------\n\n\nPREAMBLE"

        assertEquals("Preamble.\n\nSIL OPEN FONT LICENSE\n\nPREAMBLE", reflowLicenseText(text))
    }

    @Test
    fun `windows line breaks reflow too`() {
        assertEquals("one two\n\nthree", reflowLicenseText("one\r\ntwo\r\n\r\nthree\r\n"))
    }
}
