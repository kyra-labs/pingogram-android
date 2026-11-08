package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.theme.VividAvatarPalette
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.R as UiR

// What the experimental look changes in every settings page's shared parts (docs/DESIGN_SYSTEM.md §13.10): the top
// bar's height, the groups' spacing, the rows' badges and the chevrons of rows that open a page.

/**
 * The bar's expanded height: Material's medium flexible bar's, or in the experimental look (`docs/DESIGN_SYSTEM.md`
 * §13.10) the collapsed row, one line of `displaySmallWide` and the room below it, in the current font scale.
 */
@Composable
internal fun settingsTopBarExpandedHeight(): Dp {
    if (!LocalExperimentalUi.current) return TopAppBarDefaults.MediumFlexibleAppBarWithoutSubtitleExpandedHeight
    val line =
        with(LocalDensity.current) {
            MaterialTheme.experimentalTypography.displaySmallWide.lineHeight
                .toDp()
        }
    return TopAppBarDefaults.MediumAppBarCollapsedHeight + line + MaterialTheme.spacing.medium
}

/**
 * How far apart a page's groups sit: `spacing.extraLarge`, or `spacing.large` in the experimental look, whose groups
 * stand closer (`docs/DESIGN_SYSTEM.md` §13.10).
 */
@Composable
@ReadOnlyComposable
internal fun settingsGroupSpacing(): Dp =
    if (LocalExperimentalUi.current) MaterialTheme.spacing.large else MaterialTheme.spacing.extraLarge

/**
 * The chevron that ends a row opening another page in the experimental look (`docs/DESIGN_SYSTEM.md` §13.10): Material
 * Symbols' `chevron_right` in `onSurfaceVariant`, mirrored right to left by itself, decorative (the row's title
 * names the page).
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
 * Where a row sits ([index]) in its group, and a [seed] that tells the group apart, which pick the experimental look's
 * badge shape and vivid color (`docs/DESIGN_SYSTEM.md` §13.10).
 */
internal data class SettingsRowPlace(
    val index: Int,
    val seed: Int,
)

/** The row being composed's place, which [SettingsGroup] provides per row. */
internal val LocalSettingsRowPlace = staticCompositionLocalOf { SettingsRowPlace(0, 0) }

/** The vivid fill at [position] and its text color, in turn. */
internal fun VividAvatarPalette.at(position: Int): Pair<Color, Color> =
    this[position % size].let { it.container to it.content }

/**
 * The experimental look's row badges in turn down a group (`docs/DESIGN_SYSTEM.md` §13.10), as Settings home's:
 * `Cookie9Sided`, `Flower`, `Clover4Leaf`, `Sunny`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun settingsBadgeShape(index: Int): Shape =
    when (index % SETTINGS_BADGE_SHAPES) {
        0 -> MaterialShapes.Cookie9Sided.toShape()
        1 -> MaterialShapes.Flower.toShape()
        2 -> MaterialShapes.Clover4Leaf.toShape()
        else -> MaterialShapes.Sunny.toShape()
    }

private const val SETTINGS_BADGE_SHAPES = 4

/** The experimental look's filled text fields: their container a settings row's, in every state. */
@Composable
internal fun experimentalFieldColors(): TextFieldColors =
    settingsRowContainerColor().let { container ->
        TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = container,
            errorContainerColor = container,
        )
    }
