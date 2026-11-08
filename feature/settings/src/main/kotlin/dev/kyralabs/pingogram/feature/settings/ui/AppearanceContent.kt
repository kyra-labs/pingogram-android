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

// The Appearance page's groups (docs/DESIGN_SYSTEM.md §13.10, the owner's board "Appearance"): a live preview, the
// theme modes as cards, the colors as a row of chips, and the chats' text size and group bubbles on the page itself.

/**
 * The page's groups: the sample conversation ([ChatPreview]), "Light and dark" with the three
 * modes as cards ([ThemeModeCards]) and AMOLED black, "Colors" with every color theme as a chip in a row that scrolls
 * sideways ([ColorThemeRow]) and the contrast, "Chat list", and "Chats" headed by the text size and the group bubbles,
 * then its other rows.
 */
@Suppress("LongParameterList") // The settings, the chats' settings, the events, the pages and what the device offers.
@Composable
internal fun AppearanceContent(
    settings: AppearanceSettings,
    chats: ChatsSettings,
    onEvent: (AppearanceEvent) -> Unit,
    pages: AppearancePages,
    wallpaperSchemes: WallpaperSchemes?,
    systemContrast: Contrast?,
) {
    SettingsGroup {
        item { shapes -> SettingsSegment(shapes) { ChatPreview(Modifier.clip(shapes.shape)) } }
    }
    SettingsGroup(title = stringResource(R.string.settings_section_light_dark)) {
        item { shapes ->
            SettingsSegment(shapes) {
                ThemeModeCards(settings, wallpaperSchemes) { onEvent(AppearanceEvent.SetThemeMode(it)) }
            }
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_amoled),
                summary = stringResource(R.string.settings_amoled_summary),
                checked = settings.amoled,
                shapes = shapes,
                onCheckedChange = { onEvent(AppearanceEvent.SetAmoled(it)) },
            )
        }
    }
    SettingsGroup(title = stringResource(R.string.settings_section_colors)) {
        item { shapes ->
            ColorThemeRow(settings, wallpaperSchemes, shapes, pages.onOpenColorTheme) {
                onEvent(AppearanceEvent.SetColorTheme(it))
            }
        }
        item { shapes ->
            ContrastItem(
                contrast = systemContrast ?: settings.contrast,
                followsSystem = systemContrast != null,
                fallbackTheme =
                    ColorThemeOption.Palette(settings.palette).takeIf {
                        wallpaperSchemes != null && settings.dynamicColor && settings.contrast != Contrast.Standard
                    },
                shapes = shapes,
                onSelect = { onEvent(AppearanceEvent.SetContrast(it)) },
            )
        }
    }
    ChatListGroup(chats.chatList, onEvent, pages.onOpenSwipeActions)
    ChatsGroup(settings.chat, chats, onEvent, pages) {
        item { shapes ->
            TextSizeSegment(settings.chat.textScale, shapes) { onEvent(AppearanceEvent.SetChatTextScale(it)) }
        }
        item { shapes ->
            GroupBubblesSegment(settings.chat.groupBubbles, shapes) { onEvent(AppearanceEvent.SetGroupBubbles(it)) }
        }
    }
}

/** "Reset" at the end of the page's top bar: a tonal button, with Undo in its snackbar. */
@Composable
internal fun ResetAppearanceButton(onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(R.string.settings_appearance_reset))
    }
}

/**
 * The color themes as chips ([ThemeChip]) in a row that scrolls sideways, the wallpaper's colors first where the
 * device offers them, the chosen one morphed into a cookie; below them the chosen theme's name in the emphasized
 * weight, whether the wallpaper's colors are on, and "All themes", which opens the Theme page with its preview.
 */
@Composable
private fun ColorThemeRow(
    settings: AppearanceSettings,
    wallpaperSchemes: WallpaperSchemes?,
    shapes: ListItemShapes,
    onOpenThemes: () -> Unit,
    onSelect: (ColorThemeOption) -> Unit,
) {
    val selected = settings.colorTheme(wallpaperAvailable = wallpaperSchemes != null)
    val dark = settings.themeMode.isDark()
    val spacing = MaterialTheme.spacing
    SettingsSegment(shapes) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup()
                    .padding(start = spacing.large, end = spacing.large, top = spacing.large),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            if (wallpaperSchemes != null) {
                ThemeChip(
                    scheme = if (dark) wallpaperSchemes.dark else wallpaperSchemes.light,
                    name = ColorThemeOption.Wallpaper.name(),
                    selected = selected == ColorThemeOption.Wallpaper,
                    onClick = { onSelect(ColorThemeOption.Wallpaper) },
                )
            }
            ThemePalettes.forEach { palette ->
                val option = ColorThemeOption.Palette(palette)
                ThemeChip(
                    scheme = themeColorScheme(palette, dark, Contrast.Standard),
                    name = option.name(),
                    selected = selected == option,
                    onClick = { onSelect(option) },
                )
            }
        }
        ColorThemeCaption(selected, wallpaperAvailable = wallpaperSchemes != null, onOpenThemes)
    }
}

/**
 * Below the chips: the chosen theme's name in the emphasized weight, whether the wallpaper's colors are on, and
 * "All themes".
 */
@Composable
private fun ColorThemeCaption(
    selected: ColorThemeOption,
    wallpaperAvailable: Boolean,
    onOpenThemes: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = spacing.large, end = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val name = selected.name()
        val status =
            when {
                !wallpaperAvailable -> null
                selected == ColorThemeOption.Wallpaper -> stringResource(R.string.settings_wallpaper_colors_on)
                else -> stringResource(R.string.settings_wallpaper_colors_off)
            }
        Text(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        withStyle(MaterialTheme.typography.bodyMediumEmphasized.toSpanStyle()) { append(name) }
                    }
                    if (status != null) append(stringResource(R.string.settings_color_theme_status, status))
                },
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onOpenThemes, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.settings_color_theme_all))
        }
    }
}

/**
 * "Group bubbles" (§13.6) in one segment: its name over what the chosen option does, then Automatic, Vivid and Tinted
 * as a connected button group ([ChoiceRow]).
 */
@Composable
private fun GroupBubblesSegment(
    selected: GroupBubbles,
    shapes: ListItemShapes,
    onSelect: (GroupBubbles) -> Unit,
) {
    SettingsSegment(shapes) {
        SegmentedListItem(
            shapes = shapes,
            supportingContent = { Text(stringResource(selected.summaryRes())) },
            colors = nestedItemColors(),
        ) {
            Text(stringResource(R.string.settings_group_bubbles))
        }
        ChoiceRow(
            options = GroupBubbleOptions,
            selected = selected,
            label = { stringResource(it.labelRes()) },
            onSelect = onSelect,
            contentPadding =
                PaddingValues(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.large,
                ),
        )
    }
}

private val GroupBubbleOptions = persistentListOf(GroupBubbles.Automatic, GroupBubbles.Vivid, GroupBubbles.Tinted)

private fun GroupBubbles.labelRes(): Int =
    when (this) {
        GroupBubbles.Automatic -> R.string.settings_group_bubbles_automatic
        GroupBubbles.Vivid -> R.string.settings_group_bubbles_vivid
        GroupBubbles.Tinted -> R.string.settings_group_bubbles_tinted
    }

private fun GroupBubbles.summaryRes(): Int =
    when (this) {
        GroupBubbles.Automatic -> R.string.settings_group_bubbles_automatic_summary
        GroupBubbles.Vivid -> R.string.settings_group_bubbles_vivid_summary
        GroupBubbles.Tinted -> R.string.settings_group_bubbles_tinted_summary
    }
