package dev.kyralabs.pingogram.feature.chat.ui.content

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Locale

class ContentFormatTest {
    @Test
    fun `durations show minutes and seconds, and hours when there are any`() {
        assertEquals("0:00", formatDuration(0, Locale.US))
        assertEquals("0:05", formatDuration(5, Locale.US))
        assertEquals("1:05", formatDuration(65, Locale.US))
        assertEquals("59:59", formatDuration(3599, Locale.US))
        assertEquals("1:00:00", formatDuration(3600, Locale.US))
        assertEquals("10:01:05", formatDuration(36065, Locale.US))
    }

    @Test
    fun `negative durations count as zero`() {
        assertEquals("0:00", formatDuration(-3, Locale.US))
    }

    @Test
    fun `durations use the digits of the language`() {
        assertEquals("١:٠٥", formatDuration(65, Locale.forLanguageTag("ar-EG")))
    }

    @ParameterizedTest
    @CsvSource(
        "0, 0, Bytes, 0",
        "999, 999, Bytes, 0",
        "1000, 1, Kilobytes, 1",
        "1449, 1.4, Kilobytes, 1",
        "1450, 1.5, Kilobytes, 1",
        "9949, 9.9, Kilobytes, 1",
        "9950, 10, Kilobytes, 0",
        "12345, 12, Kilobytes, 0",
        "999499, 999, Kilobytes, 0",
        "999500, 1, Megabytes, 1",
        "4520000, 4.5, Megabytes, 1",
        "12400000, 12, Megabytes, 0",
        "4294967296, 4.3, Gigabytes, 1",
        "2000000000000, 2000, Gigabytes, 0",
    )
    fun `file sizes take the largest unit under 1000 with a decimal under 10`(
        bytes: Long,
        value: Double,
        unit: String,
        fractionDigits: Int,
    ) {
        assertEquals(FileSize(value, FileSizeUnit.valueOf(unit), fractionDigits), fileSize(bytes))
    }

    @Test
    fun `negative file sizes count as zero`() {
        assertEquals(FileSize(0.0, FileSizeUnit.Bytes, 0), fileSize(-1))
    }

    @Test
    fun `file size values drop trailing zeros and use the separators of the language`() {
        assertEquals("4.5", formatFileSizeValue(fileSize(4_520_000), Locale.US))
        assertEquals("4,5", formatFileSizeValue(fileSize(4_520_000), Locale.GERMANY))
        assertEquals("1", formatFileSizeValue(fileSize(1_000_000), Locale.US))
        assertEquals("10", formatFileSizeValue(fileSize(9_950_000), Locale.US))
        assertEquals("2,000", formatFileSizeValue(fileSize(2_000_000_000_000), Locale.US))
    }

    @Test
    fun `degrees have five decimals and no sign`() {
        assertEquals("52.51628", formatDegrees(52.516275, Locale.US))
        assertEquals("13.37770", formatDegrees(-13.377704, Locale.US))
        assertEquals("0.00000", formatDegrees(0.0, Locale.US))
        assertEquals("122,41942", formatDegrees(-122.419416, Locale.GERMANY))
    }
}
