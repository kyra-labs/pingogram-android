package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.ui.theme.VividAvatarPalette
import dev.kyralabs.pingogram.core.ui.theme.growInVertically
import dev.kyralabs.pingogram.core.ui.theme.isAmoledBlack
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.shrinkOutVertically
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.R as UiR

// The groups of rows on every settings screen (docs/DESIGN_SYSTEM.md §6, "Settings rows"): Material 3 Expressive
// segmented lists, each row its own container on `surface` (`surfaceContainer` with AMOLED black, settingsRowColors),
// its corners from its place in the group (ListItemDefaults.segmentedShapes), ListItemDefaults.SegmentedGap from its
// neighbours, its leading icon in a tonal circle of the group's container role (SettingsIconTone).

/**
 * The rows of a [SettingsGroup], collected in order by its builder like a lazy list's items: each [item] is composed
 * with the [ListItemShapes] of its place in the group, for its [SegmentedListItem] or [SettingsSegment]. A [key]
 * keeps a row's state (its press, its animations) with it when rows before it come and go.
 */
internal class SettingsGroupScope {
    internal val items = mutableListOf<SettingsGroupItem>()

    fun item(
        key: Any? = null,
        content: @Composable (shapes: ListItemShapes) -> Unit,
    ) {
        items += SettingsGroupItem(key, content)
    }
}

internal class SettingsGroupItem(
    val key: Any?,
    val content: @Composable (shapes: ListItemShapes) -> Unit,
)

/**
 * A group of rows, Material 3 Expressive's segmented list (`docs/DESIGN_SYSTEM.md` §6, "Settings rows"): [title], if
 * any, as a heading ([SettingsSectionHeader]), then the [items], each in a container of its own,
 * `ListItemDefaults.SegmentedGap` apart, with large outer corners at the ends of the group and small ones between rows
 * (`ListItemDefaults.segmentedShapes`), and a [footer] note below them ([SettingsFooter]). [rowsModifier] applies to
 * the rows alone, e.g. `Modifier.selectableGroup()` for radio rows. The rows' leading icons sit in circles of
 * [iconTone], one container role for the whole group (`docs/DESIGN_SYSTEM.md` §2.2).
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    rowsModifier: Modifier = Modifier,
    iconTone: SettingsIconTone = SettingsIconTone.Primary,
    items: SettingsGroupScope.() -> Unit,
) {
    val rows = SettingsGroupScope().apply(items).items
    val shown = remember { ShownRows() }
    val entries = shown.update(rows)
    val motion = MaterialTheme.motionScheme
    val enter = remember(motion) { growInVertically(motion) }
    val exit = remember(motion) { shrinkOutVertically(motion) }
    Column(modifier) {
        if (title != null) SettingsSectionHeader(title)
        CompositionLocalProvider(LocalSettingsIconTone provides iconTone) {
            Column(rowsModifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                val seed = title?.length ?: 0
                entries.forEach { entry ->
                    key(entry.key) {
                        CompositionLocalProvider(LocalSettingsRowPlace provides SettingsRowPlace(entry.index, seed)) {
                            if (entry.key is RowIndex) {
                                entry.item.content(ListItemDefaults.segmentedShapes(entry.index, entry.count))
                            } else {
                                AnimatedVisibility(visibleState = entry.visibility, enter = enter, exit = exit) {
                                    entry.item.content(ListItemDefaults.segmentedShapes(entry.index, entry.count))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (footer != null) SettingsFooter(footer)
    }
}

/** The key of a row given none: its place in the group, so it comes and goes at once, as rows did before. */
private data class RowIndex(
    val index: Int,
)

/** A row a group shows: one of its [SettingsGroupScope] items, or one on its way out, until that way ends. */
private class ShownRow(
    val key: Any,
    var item: SettingsGroupItem,
    var index: Int,
    var count: Int,
    val visibility: MutableTransitionState<Boolean>,
)

/**
 * The rows of a [SettingsGroup] as they come and go (`docs/DESIGN_SYSTEM.md` §5.1): a keyed row that comes after the
 * group first showed grows in, and one that goes shrinks away in its place, on the motion scheme's springs, so the rows
 * and groups below move rather than jump; the others keep their shapes from their place among the rows that stay. Rows
 * without a key come and go at once, as their place is all that tells them apart.
 */
private class ShownRows {
    private var rows = emptyList<ShownRow>()
    private var started = false

    /** The rows to show for [items]: theirs in order, with the rows leaving at the places they had. */
    fun update(items: List<SettingsGroupItem>): List<ShownRow> {
        val previous = rows.associateBy { it.key }
        val current =
            items.mapIndexed { index, item ->
                val key = item.key ?: RowIndex(index)
                previous[key]?.also {
                    it.item = item
                    it.index = index
                    it.count = items.size
                    it.visibility.targetState = true
                } ?: ShownRow(
                    key,
                    item,
                    index,
                    items.size,
                    MutableTransitionState(!started || key is RowIndex).apply {
                        targetState = true
                    },
                )
            }
        val currentKeys = current.mapTo(HashSet()) { it.key }
        val result = current.toMutableList()
        rows.forEachIndexed { place, row ->
            val leaving = row.key !in currentKeys && row.key !is RowIndex
            val gone = row.visibility.isIdle && !row.visibility.currentState
            if (leaving && !gone) {
                row.visibility.targetState = false
                // After the row that was above it, if it stays; at the top otherwise.
                val above = rows.subList(0, place).lastOrNull { it.key in currentKeys || it in result }
                result.add(above?.let { result.indexOf(it) + 1 } ?: 0, row)
            }
        }
        rows = result
        started = true
        return result
    }
}

/**
 * A row of a [SettingsGroup] that holds something other than a list item's slots, e.g. segmented buttons, text fields
 * or a loading indicator: [content] on the rows' container color ([settingsRowColors]), in the resting shape of
 * [shapes].
 */
@Composable
internal fun SettingsSegment(
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(settingsRowColors().containerColor, shapes.shape),
        content = content,
    )
}

/**
 * A row of a [SettingsGroup]: a [SegmentedListItem] with an outlined [icon] (a Material Symbols drawable) in a tonal
 * circle ([SettingsItemIcon]; [iconTone] for this row alone, the group's otherwise), [title] in the list item's
 * `bodyLarge`, [supportingContent] and [trailingContent]. With [onClick] it is clickable, and its shape morphs to the
 * list item's pressed shape on the motion scheme's fast spatial spring (the list item's own); without, it only shows.
 * Either way screen readers read it as one item. With [selected] as well (a page row of Settings home beside the open
 * page, `docs/DESIGN_SYSTEM.md` §7) it is selectable, in the list item's selected colors and shape while [selected]
 * is true. With [opensPage] (a row that opens another page) it ends in a chevron where it has no [trailingContent].
 */
@Suppress("LongParameterList") // A list item's slots and states, and the icon's tone.
@Composable
internal fun SettingsRow(
    title: String,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: ListItemColors = settingsRowColors(),
    iconTone: SettingsIconTone? = null,
    selected: Boolean? = null,
    opensPage: Boolean = false,
) {
    // A row that opens another page ends in a chevron (docs/DESIGN_SYSTEM.md §13.10).
    val trailingContent = trailingContent ?: if (opensPage) ({ PageChevron() }) else null
    val leadingContent: (@Composable () -> Unit)? =
        icon?.let {
            { SettingsItemIcon(it, iconTone ?: LocalSettingsIconTone.current, enabled, selected = selected == true) }
        }
    if (onClick != null && selected != null) {
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            colors = colors,
        ) {
            Text(title)
        }
    } else if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            colors = colors,
        ) {
            Text(title)
        }
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = modifier,
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            colors = colors,
        ) {
            Text(title)
        }
    }
}

/**
 * A row that opens another settings page: its [icon], [title] and [summary]; selectable with [selected] (see
 * [SettingsRow]). It ends in a chevron ([PageChevron]).
 */
@Composable
internal fun PageItem(
    title: String,
    summary: String,
    @DrawableRes icon: Int,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    selected: Boolean? = null,
) {
    SettingsRow(
        title = title,
        shapes = shapes,
        icon = icon,
        supportingContent = { Text(summary) },
        onClick = onClick,
        selected = selected,
        opensPage = true,
    )
}

/**
 * The colors of a row that holds other rows or a heading inside a [SettingsSegment]: transparent, so the segment's
 * container shows, with the segmented list item's colors otherwise (selected rows included).
 */
@Composable
internal fun nestedItemColors(): ListItemColors =
    ListItemDefaults.segmentedColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)

/**
 * The colors of a settings row (`docs/DESIGN_SYSTEM.md` §2.2): the segmented list item's, on `surface`; with AMOLED
 * black, where the page itself is black ([settingsPageColor]), on `surfaceContainer`. [contentColor] and
 * [leadingContentColor] replace the list item's, e.g. for a destructive row.
 */
@Composable
internal fun settingsRowColors(
    contentColor: Color = Color.Unspecified,
    leadingContentColor: Color = Color.Unspecified,
    supportingContentColor: Color = Color.Unspecified,
    containerColor: Color = settingsRowContainerColor(),
): ListItemColors =
    ListItemDefaults.segmentedColors(
        containerColor = containerColor,
        contentColor = contentColor,
        leadingContentColor = leadingContentColor,
        supportingContentColor = supportingContentColor,
    )

/** The container of a settings row: `surface`, or `surfaceContainer` with AMOLED black (§2.2, owner decision D22). */
@Composable
@ReadOnlyComposable
internal fun settingsRowContainerColor(): Color =
    if (isAmoledBlack()) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface

/**
 * The container role of a group's row icons (`docs/DESIGN_SYSTEM.md` §2.2, owner decision D7): one per group, as
 * Android 16's Settings colors its icons, rather than one per row; [Error] for a destructive row.
 */
internal enum class SettingsIconTone { Primary, Secondary, Tertiary, Error }

private val LocalSettingsIconTone = staticCompositionLocalOf { SettingsIconTone.Primary }

/**
 * A row's leading icon, a Material Symbols drawable, in a tonal circle of [tone]'s container role (`primaryContainer`
 * and `onPrimaryContainer`…) as wide as a list item's leading avatar, so rows keep their heights; the row's text names
 * it for screen readers. A disabled row's icon fades like its text. On a [selected] row, whose container is
 * `secondaryContainer`, the circle is `primary` and the icon `onPrimary` whatever the tone, so it stays visible
 * (`docs/DESIGN_SYSTEM.md` §2.2).
 */
@Composable
internal fun SettingsItemIcon(
    @DrawableRes icon: Int,
    tone: SettingsIconTone = LocalSettingsIconTone.current,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    // A vivid badge shape (docs/DESIGN_SYSTEM.md §13.10), but for a destructive row's and a selected row's circle.
    val vivid = tone != SettingsIconTone.Error && !selected
    val place = LocalSettingsRowPlace.current
    val (container, content) =
        when {
            selected -> colors.primary to colors.onPrimary
            vivid -> MaterialTheme.pingogramColors.vividAvatars.at(place.index + place.seed)
            else -> tone.colors(colors)
        }
    val shape = if (vivid) settingsBadgeShape(place.index) else CircleShape
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.leadingIconContainer)
                .background(if (enabled) container else colors.onSurface.copy(alpha = DISABLED_CONTAINER), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) content else colors.onSurface.copy(alpha = DISABLED_CONTENT),
        )
    }
}

/** This tone's container role and the content role on it. */
private fun SettingsIconTone.colors(colors: ColorScheme): Pair<Color, Color> =
    when (this) {
        SettingsIconTone.Primary -> colors.primaryContainer to colors.onPrimaryContainer
        SettingsIconTone.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
        SettingsIconTone.Tertiary -> colors.tertiaryContainer to colors.onTertiaryContainer
        SettingsIconTone.Error -> colors.errorContainer to colors.onErrorContainer
    }

/** Material's disabled container and content opacities (`docs/DESIGN_SYSTEM.md` §2.2). */
private const val DISABLED_CONTAINER = 0.12f
private const val DISABLED_CONTENT = 0.38f
