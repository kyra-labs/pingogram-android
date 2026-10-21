package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.ui.theme.isAmoledBlack
import dev.kyralabs.pingogram.core.ui.theme.sizes

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
    Column(modifier) {
        if (title != null) SettingsSectionHeader(title)
        CompositionLocalProvider(LocalSettingsIconTone provides iconTone) {
            Column(rowsModifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                rows.forEachIndexed { index, row ->
                    key(row.key ?: index) { row.content(ListItemDefaults.segmentedShapes(index, rows.size)) }
                }
            }
        }
        if (footer != null) SettingsFooter(footer)
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
 * Either way screen readers read it as one item.
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
) {
    val leadingContent: (@Composable () -> Unit)? =
        icon?.let { { SettingsItemIcon(it, iconTone ?: LocalSettingsIconTone.current, enabled) } }
    if (onClick != null) {
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

/** A row that opens another settings page: its [icon], [title] and [summary]. */
@Composable
internal fun PageItem(
    title: String,
    summary: String,
    @DrawableRes icon: Int,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = title,
        shapes = shapes,
        icon = icon,
        supportingContent = { Text(summary) },
        onClick = onClick,
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
): ListItemColors =
    ListItemDefaults.segmentedColors(
        containerColor = settingsRowContainerColor(),
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
 * it for screen readers. A disabled row's icon fades like its text.
 */
@Composable
internal fun SettingsItemIcon(
    @DrawableRes icon: Int,
    tone: SettingsIconTone = LocalSettingsIconTone.current,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) =
        when (tone) {
            SettingsIconTone.Primary -> colors.primaryContainer to colors.onPrimaryContainer
            SettingsIconTone.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
            SettingsIconTone.Tertiary -> colors.tertiaryContainer to colors.onTertiaryContainer
            SettingsIconTone.Error -> colors.errorContainer to colors.onErrorContainer
        }
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.leadingIconContainer)
                .background(if (enabled) container else colors.onSurface.copy(alpha = DISABLED_CONTAINER), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) content else colors.onSurface.copy(alpha = DISABLED_CONTENT),
        )
    }
}

/** Material's disabled container and content opacities (`docs/DESIGN_SYSTEM.md` §2.2). */
private const val DISABLED_CONTAINER = 0.12f
private const val DISABLED_CONTENT = 0.38f
