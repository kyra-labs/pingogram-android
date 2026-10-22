package dev.kyralabs.pingogram.feature.settings.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.ThemePalette
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of the Theme page (docs/DESIGN_SYSTEM.md §6, "Color themes"): the chips and the live preview.

/**
 * A color theme's chip, as Pixel's Wallpaper & style: a circle of [size] whose top half is [scheme]'s `primary`, its
 * bottom left `secondary` and its bottom right `tertiary`, drawn in the draw phase. While [selected] it morphs into
 * `MaterialShapes.Cookie9Sided` on the motion scheme's default spatial spring (snapping with reduced motion), with a
 * check in the scheme's `primaryContainer` in its middle. Screen readers hear it as a radio button named [name]; a
 * pick ticks (`SegmentTick`). Without [onClick] it only shows, e.g. on the Appearance row.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ThemeChip(
    scheme: ColorScheme,
    name: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = MaterialTheme.sizes.themeChip,
    onClick: (() -> Unit)? = null,
) {
    val shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, morphed = selected)
    val haptics = LocalHapticFeedback.current
    val interaction =
        if (onClick != null) {
            Modifier
                .selectable(selected = selected, role = Role.RadioButton) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                }.semantics { contentDescription = name }
        } else {
            Modifier.clearAndSetSemantics {}
        }
    Box(
        modifier =
            modifier
                .size(size)
                .then(interaction)
                .clip(shape)
                .drawBehind {
                    val half = Size(this.size.width, this.size.height / 2)
                    val quarter = Size(this.size.width / 2, this.size.height / 2)
                    drawRect(scheme.primary, size = half)
                    drawRect(scheme.secondary, topLeft = Offset(0f, half.height), size = quarter)
                    drawRect(scheme.tertiary, topLeft = Offset(quarter.width, half.height), size = quarter)
                },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Surface(shape = CircleShape, color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_check),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(MaterialTheme.spacing.extraSmall)
                            .size(IconButtonDefaults.smallIconSize),
                )
            }
        }
    }
}

/**
 * The theme in use on a few of the app's own parts, so the user sees it before leaving the page: a chat list row
 * (avatar, name, preview, time and unread badge), an incoming and an outgoing bubble, and a filled button, in the
 * roles they have in the app (`docs/DESIGN_SYSTEM.md` §2.2). Static sample content; screen readers hear one
 * description.
 */
@Composable
internal fun ThemePreview(
    themeName: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val description = stringResource(R.string.settings_color_theme_preview, themeName)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description }
                .padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(themeName, style = MaterialTheme.typography.titleSmallEmphasized, color = colors.primary)
        PreviewChatRow()
        PreviewBubble(
            text = stringResource(R.string.settings_color_theme_sample_incoming),
            outgoing = false,
        )
        PreviewBubble(
            text = stringResource(R.string.settings_color_theme_sample_outgoing),
            outgoing = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {}, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_color_theme_sample_button))
            }
        }
    }
}

/** The chat list row of [ThemePreview]: avatar, name, preview, time and unread badge, in the list's roles. */
@Composable
private fun PreviewChatRow() {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(
            photo = null,
            name = stringResource(R.string.settings_color_theme_sample_name),
            id = 2,
            size = MaterialTheme.sizes.avatarRow,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = spacing.large),
        ) {
            Text(
                stringResource(R.string.settings_color_theme_sample_name),
                style = MaterialTheme.typography.titleMediumEmphasized.userContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.settings_color_theme_sample_preview),
                style = MaterialTheme.typography.bodyMedium.userContent,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            Text(
                sampleTime(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
            )
            Badge(containerColor = colors.primary, contentColor = colors.onPrimary) { Text("2") }
        }
    }
}

/**
 * The preview's time, 9:41, as the user's language and clock write times ("9:41 AM", "9.41", "09:41"): the platform's
 * pattern, as the chat list's times (`TimePatterns`), not java.time's, whose patterns change between JDKs.
 */
@Composable
private fun sampleTime(): String {
    val locale = LocalConfiguration.current.locales[0]
    val is24HourClock = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24HourClock) {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (is24HourClock) "Hm" else "hm")
        DateTimeFormatter.ofPattern(pattern, locale).format(SampleTime)
    }
}

private val SampleTime = LocalTime.of(9, 41)

/** One bubble of [ThemePreview]: outgoing on `primaryContainer` at the end, incoming on `surfaceContainerHigh`. */
@Composable
private fun PreviewBubble(
    text: String,
    outgoing: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.bubbleMaxWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (outgoing) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (outgoing) colors.onPrimaryContainer else colors.onSurface,
        ) {
            Text(
                text = text,
                modifier =
                    Modifier.padding(
                        horizontal = MaterialTheme.spacing.medium,
                        vertical = MaterialTheme.spacing.small,
                    ),
                // Sample messages are what people write: they take their own direction, as in the conversation.
                style = MaterialTheme.typography.bodyLarge.userContent,
            )
        }
    }
}

/** What screen readers and the Appearance row call [this] theme. */
@Composable
internal fun ColorThemeOption.name(): String =
    when (this) {
        ColorThemeOption.Wallpaper -> stringResource(R.string.settings_color_theme_wallpaper)
        is ColorThemeOption.Palette -> stringResource(palette.nameRes())
    }

private fun ThemePalette.nameRes(): Int =
    when (this) {
        ThemePalette.Pingogram -> R.string.settings_color_theme_pingogram
        ThemePalette.Blue -> R.string.settings_color_theme_blue
        ThemePalette.Teal -> R.string.settings_color_theme_teal
        ThemePalette.Green -> R.string.settings_color_theme_green
        ThemePalette.Lime -> R.string.settings_color_theme_lime
        ThemePalette.Yellow -> R.string.settings_color_theme_yellow
        ThemePalette.Orange -> R.string.settings_color_theme_orange
        ThemePalette.Red -> R.string.settings_color_theme_red
        ThemePalette.Pink -> R.string.settings_color_theme_pink
        ThemePalette.Purple -> R.string.settings_color_theme_purple
        ThemePalette.Indigo -> R.string.settings_color_theme_indigo
        ThemePalette.Brown -> R.string.settings_color_theme_brown
        ThemePalette.Monochrome -> R.string.settings_color_theme_monochrome
    }

/** Every generated theme, in the order the page shows them: the brand's first. */
internal val ThemePalettes = ThemePalette.entries
