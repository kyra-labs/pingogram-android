package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// The rows of the Appearance page: the choices (theme, contrast) and the switches.

/**
 * The contrast level, one row of its group ([shapes]): a heading, why it cannot be changed ([followsSystem]) or what
 * it changes (a [fallbackTheme] the wallpaper's colors give way to at this level), and the levels below it
 * ([ChoiceRow]), in one segment.
 */
@Composable
internal fun ContrastItem(
    contrast: Contrast,
    followsSystem: Boolean,
    fallbackTheme: ColorThemeOption?,
    shapes: ListItemShapes,
    onSelect: (Contrast) -> Unit,
) {
    val note =
        when {
            followsSystem -> stringResource(R.string.settings_contrast_follows_system)
            fallbackTheme != null -> stringResource(R.string.settings_contrast_fallback_theme, fallbackTheme.name())
            else -> null
        }
    SettingsSegment(shapes) {
        SegmentedListItem(
            shapes = ListItemDefaults.shapes(),
            supportingContent = note?.let { { Text(it) } },
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
 * One of [options], inside a [SettingsSegment]: a connected button group of [ToggleButton]s
 * (`ButtonGroupDefaults.connectedLeading/Middle/TrailingButtonShapes`, `ButtonGroupDefaults.ConnectedSpaceBetween`
 * apart, sharing the width), the selected one checked and a full pill, padded by [contentPadding], where every label
 * fits its button on one line; otherwise (a large font scale, long translations) a list of [RadioItem]s, so no label is
 * ever broken or truncated. Labels are measured in the selected button's style, `labelLargeEmphasized`. Screen readers
 * hear the buttons as radio buttons of one group; choosing one ticks (`SegmentTick`, `docs/DESIGN_SYSTEM.md` §5), and
 * choosing the selected one again does nothing.
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
    val haptics = LocalHapticFeedback.current
    val select = { option: T ->
        if (option != selected) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSelect(option)
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLargeEmphasized
    val layoutDirection = LocalLayoutDirection.current
    val buttonPadding = ToggleButtonDefaults.contentPaddingFor(ToggleButtonDefaults.MinHeight)
    // What a button takes besides its label: its padding, and its share of the gaps between the buttons.
    val buttonChrome =
        buttonPadding.calculateStartPadding(layoutDirection) +
            buttonPadding.calculateEndPadding(layoutDirection) +
            ButtonGroupDefaults.ConnectedSpaceBetween
    val rowPadding =
        contentPadding.calculateStartPadding(layoutDirection) + contentPadding.calculateEndPadding(layoutDirection)
    val density = LocalDensity.current
    val widestLabel = remember(labels, style, density) { labels.maxOf { textMeasurer.measure(it, style).size.width } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val buttonWidth = with(density) { ((maxWidth - rowPadding) / options.size - buttonChrome).roundToPx() }
        if (widestLabel <= buttonWidth) {
            ConnectedChoices(options, selected, labels, select, enabled, Modifier.padding(contentPadding))
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
 * [ChoiceRow]'s connected button group: Material 3 Expressive's single-select connected [ToggleButton]s, which morph
 * on press and to a full pill when checked, on `surfaceContainerHighest` while unchecked so they stand apart from the
 * row's container.
 */
@Composable
private fun <T> ConnectedChoices(
    options: ImmutableList<T>,
    selected: T,
    labels: List<String>,
    onSelect: (T) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val typography = MaterialTheme.typography
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                enabled = enabled,
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors = colors,
            ) {
                Text(
                    text = labels[index],
                    maxLines = 1,
                    style = if (checked) typography.labelLargeEmphasized else typography.labelLarge,
                )
            }
        }
    }
}

/**
 * One of several options in a `selectableGroup`: Material 3 Expressive's single-selection [SegmentedListItem], with a
 * [RadioButton] and [label], an optional [summary] and [trailingContent]. The whole row selects it, screen readers hear
 * it as a radio button, and the selected one takes the list item's selected container (`secondaryContainer`) and its
 * rounder selected shape. [shapes] places it in its group ([SettingsGroup]); in a [ChoiceRow], [colors] leave the
 * segment's container to show while it is not selected. A [labelLanguage] (a BCP 47 tag) marks [label] as text in that
 * language, a language's own name: it takes its own direction and screen readers read it in that language.
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
    colors: ListItemColors = settingsRowColors(),
    labelLanguage: String? = null,
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
        if (labelLanguage == null) {
            Text(label)
        } else {
            // A language's own name: in its own direction, and read by screen readers in its language.
            val text =
                remember(label, labelLanguage) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(localeList = LocaleList(labelLanguage))) { append(label) }
                    }
                }
            Text(text, style = LocalTextStyle.current.userContent)
        }
    }
}

/**
 * A setting that is on or off, a row of its group ([shapes]): the whole row toggles its switch ([CheckSwitch], a check
 * in its thumb while on) with a `ToggleOn` or `ToggleOff` haptic, and screen readers hear it as a switch. The
 * clickable list item rather than its toggleable variant, which screen readers would call a checkbox and which would
 * show the row as selected while on.
 */
@Composable
internal fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    shapes: ListItemShapes,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        onClick = {
            haptics.performToggle(!checked)
            onCheckedChange(!checked)
        },
        shapes = shapes,
        modifier =
            Modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        trailingContent = { CheckSwitch(checked = checked) },
        supportingContent = { Text(summary) },
        colors = settingsRowColors(),
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
