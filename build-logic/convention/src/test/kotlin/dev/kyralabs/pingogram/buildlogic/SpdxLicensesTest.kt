package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class SpdxLicensesTest {
    private fun idOf(
        name: String?,
        url: String?,
    ): String? = (SpdxLicenses.match(PomLicense(name, url)) as? SpdxLicenses.Match.Found)?.license?.id

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        nullValues = ["-"],
        value = [
            "The Apache Software License, Version 2.0 | http://www.apache.org/licenses/LICENSE-2.0.txt | Apache-2.0",
            "Apache License, Version 2.0              | https://www.apache.org/licenses/LICENSE-2.0    | Apache-2.0",
            "Apache-2.0                               | -                                              | Apache-2.0",
            "-                                        | https://apache.org/licenses/LICENSE-2.0.html/  | Apache-2.0",
            "BSD-3-Clause                             | https://opensource.org/licenses/BSD-3-Clause   | BSD-3-Clause",
            "-                                        | http://opensource.org/licenses/BSD-3-Clause/   | BSD-3-Clause",
            "Boost Software License 1.0               | -                                              | BSL-1.0",
            "Android Software Development Kit License | -                                    | LicenseRef-Android-SDK",
            "- | https://developer.android.com/studio/terms.html | LicenseRef-Android-SDK",
        ],
    )
    fun `maps the spellings and links POMs use to SPDX ids`(
        name: String?,
        url: String?,
        id: String,
    ) {
        assertEquals(id, idOf(name, url))
    }

    @Test
    fun `an unknown license matches nothing`() {
        assertEquals(SpdxLicenses.Match.Unknown, SpdxLicenses.match(PomLicense("Proprietary", "https://example.com")))
    }

    @Test
    fun `a name and a link of two different licenses are a conflict`() {
        val match = SpdxLicenses.match(PomLicense("BSD-3-Clause", "https://www.apache.org/licenses/LICENSE-2.0"))

        assertEquals(
            SpdxLicenses.Match.Conflict(SpdxLicenses["BSD-3-Clause"]!!, SpdxLicenses["Apache-2.0"]!!),
            match,
        )
    }

    @Test
    fun `every license in the table has its text in config-licenses-texts`() {
        // Gradle runs the tests in build-logic/convention; the texts are in the repository root.
        val texts = File("../../${OpenSourceLicenses.TEXTS_DIRECTORY}")

        val missing = SpdxLicenses.all.map { it.id }.filterNot { texts.resolve("$it.txt").isFile }

        assertTrue(missing.isEmpty(), "No text in $texts for $missing")
    }
}
