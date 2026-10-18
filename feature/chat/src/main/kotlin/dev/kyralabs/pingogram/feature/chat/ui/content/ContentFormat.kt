package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.feature.chat.R
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

// Lengths, file sizes and coordinates in content: pure functions (unit-tested) and their localized strings.

/** A media length or position, "0:05", "1:05" or "1:01:05", in the digits of [locale]. Negative counts as 0. */
internal fun formatDuration(
    seconds: Int,
    locale: Locale,
): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / SECONDS_PER_HOUR
    val minutes = total / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
    val rest = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(locale, "%d:%02d", minutes, rest)
    }
}

/** [formatDuration] in the current configuration's language. */
@Composable
@ReadOnlyComposable
internal fun mediaDurationText(seconds: Int): String = formatDuration(seconds, currentLocale())

/** A unit of [FileSize]: decimal, as Android shows sizes (1 kB = 1000 bytes). */
internal enum class FileSizeUnit {
    Bytes,
    Kilobytes,
    Megabytes,
    Gigabytes,
}

/** A file size for people: [value] in [unit], rounded to [fractionDigits]. */
internal data class FileSize(
    val value: Double,
    val unit: FileSizeUnit,
    val fractionDigits: Int,
)

/**
 * [bytes] in the largest unit that keeps the value under 1000, as Android's `Formatter.formatShortFileSize` does: one
 * decimal under 10 ("4.5 MB"), none from 10 ("12 MB"), none for bytes. A value that rounds up to 1000 moves to the
 * next unit ("1 MB", not "1000 kB"). Gigabytes are the largest unit; Telegram files are at most 4 GB.
 */
internal fun fileSize(bytes: Long): FileSize {
    val size = bytes.coerceAtLeast(0)
    var unitBytes = 1L
    var unit = FileSizeUnit.Bytes
    while (true) {
        // In whole numbers, so that 1450 bytes are 1.5 kB: in binary floating point, 1.45 is a little less.
        val tenths = roundedDivision(size * DECIMAL_BASE, unitBytes)
        val fractionDigits = if (unit == FileSizeUnit.Bytes || tenths >= DECIMALS_BELOW * DECIMAL_BASE) 0 else 1
        val value =
            if (fractionDigits == 0) {
                roundedDivision(size, unitBytes).toDouble()
            } else {
                tenths.toDouble() / DECIMAL_BASE
            }
        if (value < UNIT_STEP || unit == FileSizeUnit.entries.last()) return FileSize(value, unit, fractionDigits)
        unitBytes *= UNIT_STEP
        unit = FileSizeUnit.entries[unit.ordinal + 1]
    }
}

/** [dividend] / [divisor], rounded half up; both are 0 or more. */
private fun roundedDivision(
    dividend: Long,
    divisor: Long,
): Long = (dividend + divisor / 2) / divisor

/** The number of [size] with the digits and decimal separator of [locale], without trailing zeros ("1", "4.5"). */
internal fun formatFileSizeValue(
    size: FileSize,
    locale: Locale,
): String =
    NumberFormat
        .getNumberInstance(locale)
        .apply {
            maximumFractionDigits = size.fractionDigits
            minimumFractionDigits = 0
            roundingMode = RoundingMode.HALF_UP
        }.format(size.value)

/** [bytes] as "4.5 MB" in the current configuration's language. */
@Composable
@ReadOnlyComposable
internal fun fileSizeText(bytes: Long): String {
    val size = fileSize(bytes)
    val value = formatFileSizeValue(size, currentLocale())
    return when (size.unit) {
        FileSizeUnit.Bytes -> stringResource(R.string.content_size_bytes, value)
        FileSizeUnit.Kilobytes -> stringResource(R.string.content_size_kilobytes, value)
        FileSizeUnit.Megabytes -> stringResource(R.string.content_size_megabytes, value)
        FileSizeUnit.Gigabytes -> stringResource(R.string.content_size_gigabytes, value)
    }
}

/**
 * The size of a coordinate in degrees, without its sign (the hemisphere says it), with five decimals (about a metre)
 * in the digits and decimal separator of [locale].
 */
internal fun formatDegrees(
    degrees: Double,
    locale: Locale,
): String =
    NumberFormat
        .getNumberInstance(locale)
        .apply {
            maximumFractionDigits = COORDINATE_DECIMALS
            minimumFractionDigits = COORDINATE_DECIMALS
            roundingMode = RoundingMode.HALF_UP
            isGroupingUsed = false
        }.format(abs(degrees))

/** [location] as "52.52001° N, 13.40495° E" in the current configuration's language. */
@Composable
@ReadOnlyComposable
internal fun coordinatesText(location: Location): String {
    val locale = currentLocale()
    val latitude = formatDegrees(location.latitude, locale)
    val longitude = formatDegrees(location.longitude, locale)
    return stringResource(
        R.string.content_coordinates,
        if (location.latitude >= 0) {
            stringResource(R.string.content_latitude_north, latitude)
        } else {
            stringResource(R.string.content_latitude_south, latitude)
        },
        if (location.longitude >= 0) {
            stringResource(R.string.content_longitude_east, longitude)
        } else {
            stringResource(R.string.content_longitude_west, longitude)
        },
    )
}

@Composable
@ReadOnlyComposable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_HOUR = 3600
private const val UNIT_STEP = 1000L
private const val DECIMALS_BELOW = 10L
private const val DECIMAL_BASE = 10L
private const val COORDINATE_DECIMALS = 5
