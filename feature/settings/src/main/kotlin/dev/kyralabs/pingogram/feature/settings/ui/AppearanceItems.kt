package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
import androidx.compose.ui.text.rememberTextMeasurer
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// The rows of the Appearance page: the choices (theme, contrast) and the switches.

/**
 * The contrast level: a heading, why it cannot be changed ([followsSystem]) or what it changes ([usesBrandColors]),
 * and the levels ([ChoiceRow]).
 */
@Composable
internal fun ContrastItem(
    contrast: Contrast,
    followsSystem: Boolean,
    usesBrandColors: Boolean,
    onSelect: (Contrast) -> Unit,
) {
    val note =
        when {
            followsSystem -> R.string.settings_contrast_follows_system
            usesBrandColors -> R.string.settings_contrast_brand_colors
            else -> null
        }
    ListItem(
        supportingContent = note?.let { { Text(stringResource(it)) } },
        colors = settingsItemColors(),
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

/**
 * One of [options]: connected [SegmentedButton]s, the selected one checked, where every label fits its segment on one
 * line; otherwise (a large font scale, long translations) a list of radio buttons, so no label is ever broken or
 * truncated. Labels are measured in the segmented buttons' style, `labelLarge`. Selecting the selected option again
 * does nothing.
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
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { index, option ->
                    RadioItem(
                        label = labels[index],
                        selected = option == selected,
                        enabled = enabled,
                        onClick = { select(option) },
                    )
                }
            }
        }
    }
}

/** An option of a [ChoiceRow] that does not fit as segmented buttons: the whole row selects it. */
@Composable
private fun RadioItem(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier.selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        enabled = enabled,
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        colors = settingsItemColors(),
    ) {
        Text(label)
    }
}

/** A setting that is on or off: the whole row toggles its [Switch], and screen readers hear it as a switch. */
@Composable
internal fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        supportingContent = { Text(summary) },
        colors = settingsItemColors(),
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
