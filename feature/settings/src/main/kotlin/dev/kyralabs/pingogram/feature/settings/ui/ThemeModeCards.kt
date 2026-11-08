package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.GroupBubbles
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.isDark
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.themeColorScheme
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

// The experimental Appearance page's theme mode cards (docs/DESIGN_SYSTEM.md §13.10): System, Light and Dark, each
// a sample of the app in its scheme.

/**
 * System, Light and Dark as cards sharing the segment's width: a sample of the app in the theme's light or dark scheme
 * (both, split along the diagonal, for System), its name below; the chosen one ringed in `primary` with a check, its
 * name in the emphasized weight. One radio group for screen readers, a `SegmentTick` per pick.
 */
@Composable
internal fun ThemeModeCards(
    settings: AppearanceSettings,
    wallpaperSchemes: WallpaperSchemes?,
    onSelect: (ThemeMode) -> Unit,
) {
    val light = sampleScheme(settings, wallpaperSchemes, dark = false)
    val dark = sampleScheme(settings, wallpaperSchemes, dark = true)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        ThemeModes.forEach { mode ->
            ThemeModeCard(
                mode = mode,
                light = light,
                dark = dark,
                selected = mode == settings.themeMode,
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Suppress("LongParameterList") // The mode, its two schemes, its state, its click and the modifier.
@Composable
private fun ThemeModeCard(
    mode: ThemeMode,
    light: ColorScheme,
    dark: ColorScheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.largeIncreased
    Column(
        modifier =
            modifier.selectable(selected = selected, role = Role.RadioButton) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(CARD_ASPECT_RATIO)
                    .border(MaterialTheme.sizes.avatarRing, if (selected) colors.primary else Color.Transparent, shape)
                    .padding(MaterialTheme.spacing.extraSmall)
                    .clip(MaterialTheme.shapes.medium)
                    .drawBehind {
                        when (mode) {
                            ThemeMode.Light -> drawSample(light)
                            ThemeMode.Dark -> drawSample(dark)
                            ThemeMode.System -> drawSplitSample(light, dark)
                        }
                    },
        ) {
            if (selected) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacing.extraSmall),
                    shape = CircleShape,
                    color = colors.primary,
                    contentColor = colors.onPrimary,
                ) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_check),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .padding(
                                    MaterialTheme.spacing.extraSmall,
                                ).size(IconButtonDefaults.smallIconSize),
                    )
                }
            }
        }
        val typography = MaterialTheme.typography
        Text(
            text = stringResource(mode.labelRes()),
            style = if (selected) typography.labelLargeEmphasized else typography.labelLarge,
        )
    }
}

/**
 * The theme's [dark] or light scheme: the wallpaper's where they are in use, else the color theme's at standard
 * contrast.
 */
@Composable
private fun sampleScheme(
    settings: AppearanceSettings,
    wallpaperSchemes: WallpaperSchemes?,
    dark: Boolean,
): ColorScheme =
    if (settings.dynamicColor && wallpaperSchemes != null) {
        if (dark) wallpaperSchemes.dark else wallpaperSchemes.light
    } else {
        themeColorScheme(settings.palette, dark, Contrast.Standard)
    }

/** A sample of the app in [scheme]: a title line, two incoming bubbles around an outgoing one, and the composer. */
private fun DrawScope.drawSample(scheme: ColorScheme) {
    drawRect(scheme.surface)
    val radius = CornerRadius(size.height * SAMPLE_CORNER)
    SampleShapes.forEach { (part, rect) ->
        val color =
            when (part) {
                SamplePart.Ink -> scheme.onSurfaceVariant
                SamplePart.Incoming -> scheme.surfaceContainerHighest
                SamplePart.Outgoing -> scheme.primaryContainer
                SamplePart.Composer -> scheme.surfaceContainerHigh
            }
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * rect.left, size.height * rect.top),
            size = Size(size.width * rect.width, size.height * rect.height),
            cornerRadius = radius,
        )
    }
}

/** The light sample above the diagonal from the top end to the bottom start, the dark one below it. */
private fun DrawScope.drawSplitSample(
    light: ColorScheme,
    dark: ColorScheme,
) {
    drawSample(light)
    val lower =
        Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
    clipPath(lower) { drawSample(dark) }
}

private enum class SamplePart { Ink, Incoming, Outgoing, Composer }

/** Where a part of the sample sits, as fractions of the sample's size. */
private class SampleRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/** The sample's parts, top to bottom. */
private val SampleShapes: List<Pair<SamplePart, SampleRect>> =
    listOf(
        SamplePart.Ink to SampleRect(left = 0.1f, top = 0.09f, width = 0.42f, height = 0.05f),
        SamplePart.Incoming to SampleRect(left = 0.1f, top = 0.24f, width = 0.5f, height = 0.12f),
        SamplePart.Outgoing to SampleRect(left = 0.42f, top = 0.41f, width = 0.48f, height = 0.12f),
        SamplePart.Incoming to SampleRect(left = 0.1f, top = 0.58f, width = 0.6f, height = 0.12f),
        SamplePart.Composer to SampleRect(left = 0.22f, top = 0.84f, width = 0.56f, height = 0.08f),
    )

/** A card's width to its height, as a phone's screen, roughly. */
private const val CARD_ASPECT_RATIO = 0.8f

/** The sample's corners, as a fraction of its height. */
private const val SAMPLE_CORNER = 0.06f
