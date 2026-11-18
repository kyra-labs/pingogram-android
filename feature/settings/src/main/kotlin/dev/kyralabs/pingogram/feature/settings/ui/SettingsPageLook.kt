package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.R as UiR

// Every settings page's shared look (docs/DESIGN_SYSTEM.md §13.10): the top bar's height, the groups' spacing and
// category colors, and the chevrons of rows that open a page.

/**
 * The bar's expanded height (`docs/DESIGN_SYSTEM.md` §13.10): the collapsed row, one line of `displaySmallWide` and the
 * room below it, in the current font scale.
 */
@Composable
internal fun settingsTopBarExpandedHeight(): Dp {
    val line =
        with(LocalDensity.current) {
            MaterialTheme.experimentalTypography.displaySmallWide.lineHeight
                .toDp()
        }
    return TopAppBarDefaults.MediumAppBarCollapsedHeight + line + MaterialTheme.spacing.medium
}

/**
 * How far apart a page's groups sit: `spacing.large` (`docs/DESIGN_SYSTEM.md` §13.10).
 */
@Composable
@ReadOnlyComposable
internal fun settingsGroupSpacing(): Dp = MaterialTheme.spacing.large

/**
 * The chevron that ends a row opening another page (`docs/DESIGN_SYSTEM.md` §13.10): Material Symbols'
 * `chevron_right` in `onSurfaceVariant`, mirrored right to left by itself, decorative (the row's title names the page).
 */
@Composable
internal fun PageChevron() {
    Icon(
        painter = painterResource(UiR.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Which category color each group of a page takes (`docs/DESIGN_SYSTEM.md` §13.10): the next one down the page, in the
 * order the groups are first composed, so neighbouring groups differ. A group with a title keeps its color when it
 * goes and comes back; one without takes the next. Saved with the page, so the colors outlast a rotation, process
 * death and a lazy list's rows scrolling away.
 */
internal class SettingsGroupOrder private constructor(
    private var next: Int,
    private val titled: MutableMap<String, Int>,
) {
    constructor() : this(0, HashMap())

    /** The color index of the group titled [title], or of a group without a title composed for the first time. */
    fun indexOf(title: String?): Int = if (title == null) next++ else titled.getOrPut(title) { next++ }

    companion object {
        /** Saves the next index and the titled groups' indices, in that order. */
        val Saver: Saver<SettingsGroupOrder, Any> =
            listSaver(
                save = { order -> listOf<Any>(order.next) + order.titled.flatMap { it.toPair().toList() } },
                restore = { saved ->
                    val titled = HashMap<String, Int>()
                    saved.drop(1).chunked(2).forEach { titled[it[0] as String] = it[1] as Int }
                    SettingsGroupOrder(saved.first() as Int, titled)
                },
            )
    }
}

/** The page's [SettingsGroupOrder], which [SettingsPageFrame] provides; none outside a page (index 0). */
internal val LocalSettingsGroupOrder = staticCompositionLocalOf<SettingsGroupOrder?> { null }

/** The category color index of the group titled [title] being composed ([SettingsGroupOrder]). */
@Composable
internal fun settingsGroupCategory(title: String?): Int {
    val order = LocalSettingsGroupOrder.current ?: return 0
    return if (title != null) {
        remember(order, title) { order.indexOf(title) }
    } else {
        rememberSaveable { order.indexOf(null) }
    }
}

/** The filled text fields: their container a settings row's, in every state. */
@Composable
internal fun settingsFieldColors(): TextFieldColors =
    settingsRowContainerColor().let { container ->
        TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            errorContainerColor = container,
        )
    }
