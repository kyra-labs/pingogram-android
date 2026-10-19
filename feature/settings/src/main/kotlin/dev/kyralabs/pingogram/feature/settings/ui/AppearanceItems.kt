package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.rememberTextMeasurer
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// The rows of the Appearance page: the choices (theme, contrast) and the switches.

/**
 * The contrast level, one row of its group ([shapes]): a heading, why it cannot be changed ([followsSystem]) or what
 * it changes ([usesBrandColors]), and the levels below it ([ChoiceRow]), in one segment.
 */
@Composable
internal fun ContrastItem(
    contrast: Contrast,
    followsSystem: Boolean,
    usesBrandColors: Boolean,
    shapes: ListItemShapes,
    onSelect: (Contrast) -> Unit,
) {
    val note =
        when {
            followsSystem -> R.string.settings_contrast_follows_system
            usesBrandColors -> R.string.settings_contrast_brand_colors
            else -> null
        }
    SettingsSegment(shapes) {
        SegmentedListItem(
            shapes = ListItemDefaults.shapes(),
            supportingContent = note?.let { { Text(stringResource(it)) } },
            colors = nestedItemColors(),
        ) {
            Text(stringResource(R.string.settings_contrast))
        }
        ChoiceRow(
            options = ContrastLevels,
            selected = contrast,
            label = { stringResource(it.labelRes()) },
            onSelect = onSelect,
            enabled = !followsSystem,
            contentPadding =
                PaddingValues(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.large,
                ),
        )
    }
}

/**
 * One of [options], inside a [SettingsSegment]: connected [SegmentedButton]s, the selected one checked, padded by
 * [contentPadding], where every label fits its segment on one line; otherwise (a large font scale, long translations)
 * a list of [RadioItem]s, so no label is ever broken or truncated. Labels are measured in the segmented buttons' style,
 * `labelLarge`. Selecting the selected option again does nothing.
 */
@Composable
internal fun <T> ChoiceRow(
    options: ImmutableList<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    contentPadding: PaddingValues,
    enabled: Boolean = true,
) {
    val labels = options.map { label(it) }
    val select = { option: T -> if (option != selected) onSelect(option) }
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val layoutDirection = LocalLayoutDirection.current
    // What a segment takes besides its label: its padding, and its check icon with the gap before the label.
    val segmentChrome =
        SegmentedButtonDefaults.ContentPadding.calculateStartPadding(layoutDirection) +
            SegmentedButtonDefaults.ContentPadding.calculateEndPadding(layoutDirection) +
            SegmentedButtonDefaults.IconSize +
            MaterialTheme.spacing.small
    val rowPadding =
        contentPadding.calculateStartPadding(layoutDirection) + contentPadding.calculateEndPadding(layoutDirection)
    val density = LocalDensity.current
    val widestLabel = remember(labels, style, density) { labels.maxOf { textMeasurer.measure(it, style).size.width } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val segmentWidth = with(density) { ((maxWidth - rowPadding) / options.size - segmentChrome).roundToPx() }
        if (widestLabel <= segmentWidth) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
            ) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { select(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        enabled = enabled,
                    ) {
                        Text(text = labels[index], maxLines = 1)
                    }
                }
            }
        } else {
            // Inset in the segment, so a selected or pressed option's own container shows as a shape of its own.
            Column(
                Modifier
                    .selectableGroup()
                    .padding(MaterialTheme.spacing.small),
            ) {
                options.forEachIndexed { index, option ->
                    RadioItem(
                        label = labels[index],
                        selected = option == selected,
                        enabled = enabled,
                        shapes = ListItemDefaults.shapes(),
                        onClick = { select(option) },
                        colors = nestedItemColors(),
                    )
                }
            }
        }
    }
}

/**
 * One of several options in a `selectableGroup`: Material 3 Expressive's single-selection [SegmentedListItem], with a
 * [RadioButton] and [label], an optional [summary] and [trailingContent]. The whole row selects it, screen readers hear
 * it as a radio button, and the selected one takes the list item's selected container (`secondaryContainer`) and its
 * rounder selected shape. [shapes] places it in its group ([SettingsGroup]); in a [ChoiceRow], [colors] leave the
 * segment's container to show while it is not selected.
 */
@Composable
internal fun RadioItem(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    summary: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        enabled = enabled,
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        trailingContent = trailingContent,
        supportingContent = summary?.let { { Text(it) } },
        colors = colors,
    ) {
        Text(label)
    }
}

/**
 * A setting that is on or off, a row of its group ([shapes]): the whole row toggles its [Switch], and screen readers
 * hear it as a switch. The clickable list item rather than its toggleable variant, which screen readers would call a
 * checkbox and which would show the row as selected while on.
 */
@Composable
internal fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    shapes: ListItemShapes,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = shapes,
        modifier =
            Modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        supportingContent = { Text(summary) },
    ) {
        Text(title)
    }
}

internal val ThemeModes = ThemeMode.entries.toImmutableList()
private val ContrastLevels = Contrast.entries.toImmutableList()

internal fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.System -> R.string.settings_theme_system
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
    }

private fun Contrast.labelRes(): Int =
    when (this) {
        Contrast.Standard -> R.string.settings_contrast_standard
        Contrast.Medium -> R.string.settings_contrast_medium
        Contrast.High -> R.string.settings_contrast_high
    }
