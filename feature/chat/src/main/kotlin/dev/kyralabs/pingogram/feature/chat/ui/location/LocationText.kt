package dev.kyralabs.pingogram.feature.chat.ui.location

import android.content.res.Resources
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.feature.chat.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import android.icu.text.NumberFormat as IcuNumberFormat

// How the "Location" sheet writes a location, in the user's language.

/**
 * [location] as "48.85837° N, 2.29448° E": five decimals (about a metre), the hemisphere as a letter, digits and
 * decimal separator in [locale]. With [spoken], as screen readers say it: "48.85837 degrees north, …".
 */
internal fun Resources.coordinatesText(
    location: Location,
    locale: Locale,
    spoken: Boolean = false,
): String {
    val degrees =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = COORDINATE_DECIMALS
            maximumFractionDigits = COORDINATE_DECIMALS
        }
    val latitude =
        getString(hemisphere(location.latitude >= 0, spoken, north = true), degrees.format(abs(location.latitude)))
    val longitude =
        getString(hemisphere(location.longitude >= 0, spoken, north = false), degrees.format(abs(location.longitude)))
    return getString(R.string.location_coordinates, latitude, longitude)
}

@StringRes
private fun hemisphere(
    positive: Boolean,
    spoken: Boolean,
    north: Boolean,
): Int =
    when {
        north && positive -> if (spoken) R.string.location_north_spoken else R.string.location_north
        north -> if (spoken) R.string.location_south_spoken else R.string.location_south
        positive -> if (spoken) R.string.location_east_spoken else R.string.location_east
        else -> if (spoken) R.string.location_west_spoken else R.string.location_west
    }

/**
 * A location's accuracy as a distance in [locale]'s short units: whole metres below a kilometre ("12 m"), kilometres
 * with one decimal from there ("1.5 km").
 */
internal fun accuracyText(
    meters: Float,
    locale: Locale,
): String {
    val number = IcuNumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT, number)
    val measure =
        if (meters < METERS_PER_KILOMETER) {
            Measure(meters.roundToInt().coerceAtLeast(1), MeasureUnit.METER)
        } else {
            Measure(meters / METERS_PER_KILOMETER, MeasureUnit.KILOMETER)
        }
    return format.format(measure)
}

private const val COORDINATE_DECIMALS = 5
private const val METERS_PER_KILOMETER = 1_000f
