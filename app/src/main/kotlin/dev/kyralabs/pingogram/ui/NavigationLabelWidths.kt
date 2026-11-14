package dev.kyralabs.pingogram.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.theme.pingogramTypography
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import java.util.concurrent.ConcurrentHashMap

/**
 * How wide the floating navigation bar's labels are, kept for the process (`docs/PERFORMANCE.md` §2): every tab's label
 * in `labelLarge`, plus the most one grows when it is the selected tab's `labelLargeEmphasized`. [labelsFit] adds the
 * tabs' padding and the selected tab's icon to decide whether the labels show.
 *
 * Measuring the three labels in two styles cost the first frame of every start (`StartupBenchmark`, without a baseline
 * profile: cold +19%, the warm first frame +18–22% after #494). Now [warmUp] measures them off the main thread while
 * the activity is created, so the first frame finds the width here; a warm start, in the same process, measures
 * nothing. Where the first frame asks for a [Key] [warmUp] did not measure (high contrast's grade, another language
 * before Android 13), [labelsFit] measures in the same way, so the labels never switch after.
 */
internal object NavigationLabelWidths {
    /** What the labels' width depends on. */
    data class Key(
        val labels: List<String>,
        val label: TextStyle,
        val emphasized: TextStyle,
        val density: Float,
        val fontScale: Float,
        val layoutDirection: LayoutDirection,
    )

    private val widths = ConcurrentHashMap<Key, Int>()

    /** The labels' width of [key] in pixels: kept, or measured with the [TextMeasurer] [measurer] makes and kept. */
    fun widthPx(
        key: Key,
        measurer: () -> TextMeasurer,
    ): Int = widths[key] ?: measure(key, measurer()).also { widths[key] = it }

    /** The width kept for [key], if any. */
    fun cached(key: Key): Int? = widths[key]

    /**
     * Measures the labels as the first frame will ask for them, with the context's language, density, font scale and
     * bold text and the standard contrast's typography. Blocking: runs on a background thread.
     */
    fun warmUp(context: Context) {
        val configuration = context.resources.configuration
        val density = Density(context)
        val typography = pingogramTypography(fontWeightAdjustment = configuration.weightAdjustment())
        val key =
            Key(
                labels = TopLevelDestination.entries.map { context.getString(it.label) },
                label = typography.labelLarge,
                emphasized = typography.labelLargeEmphasized,
                density = density.density,
                fontScale = density.fontScale,
                layoutDirection =
                    if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
            )
        widthPx(key) { TextMeasurer(createFontFamilyResolver(context), density, key.layoutDirection) }
    }

    /** Forgets every width, for tests. */
    fun clear() = widths.clear()

    /** Measures the labels' width of [key] with [measurer], keeping nothing. */
    fun measure(
        key: Key,
        measurer: TextMeasurer,
    ): Int {
        fun width(
            label: String,
            style: TextStyle,
        ): Int = measurer.measure(label, style, maxLines = 1).size.width

        val plain = key.labels.map { width(it, key.label) }
        val growth = key.labels.indices.maxOf { width(key.labels[it], key.emphasized) - plain[it] }
        return plain.sum() + growth
    }

    /** Bold text's weight adjustment (Android 12+), as `PingogramTheme` passes it to the typography. */
    private fun Configuration.weightAdjustment(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            fontWeightAdjustment.takeIf { it != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED } ?: 0
        } else {
            0
        }
}

/**
 * What the bar's labels' width depends on in this composition ([NavigationLabelWidths.Key]): the labels, their styles,
 * the density and the layout direction.
 */
@Composable
internal fun rememberNavigationLabelsKey(): NavigationLabelWidths.Key {
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val labels = TopLevelDestination.entries.map { stringResource(it.label) }
    return remember(labels, typography, density, layoutDirection) {
        NavigationLabelWidths.Key(
            labels = labels,
            label = typography.labelLarge,
            emphasized = typography.labelLargeEmphasized,
            density = density.density,
            fontScale = density.fontScale,
            layoutDirection = layoutDirection,
        )
    }
}
