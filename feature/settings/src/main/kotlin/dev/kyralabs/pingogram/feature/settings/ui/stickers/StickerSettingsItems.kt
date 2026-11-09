package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.repository.StickerSettings
import dev.kyralabs.pingogram.core.domain.repository.StickerSuggestions
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListRowStyle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSectionHeader
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.nestedItemColors
import dev.kyralabs.pingogram.feature.settings.ui.settingsListRow
import kotlinx.collections.immutable.toImmutableList

/**
 * The stickers' own settings on the stickers' page, under "Settings": "Suggest stickers by emoji" (All sets, My sets,
 * Off as [ChoiceRow]'s connected buttons in a segment, over what the choice does) and "Loop animated stickers" (a
 * switch row). Each change is saved at once ([StickersEvent.SetSuggestions], [StickersEvent.SetLoop]).
 */
internal fun LazyListScope.stickerSettings(
    settings: StickerSettings,
    style: SettingsListRowStyle,
    onEvent: (StickersEvent) -> Unit,
) {
    item(key = SETTINGS_HEADER, contentType = SETTINGS_HEADER) {
        SettingsSectionHeader(
            stringResource(R.string.settings_stickers_section_settings),
            Modifier
                .settingsListRow(this, style)
                .padding(top = MaterialTheme.spacing.extraLarge - ListItemDefaults.SegmentedGap),
        )
    }
    item(key = SUGGESTIONS, contentType = SUGGESTIONS) {
        Box(Modifier.settingsListRow(this, style)) {
            SettingsSegment(ListItemDefaults.segmentedShapes(0, 2)) {
                SegmentedListItem(
                    shapes = ListItemDefaults.shapes(),
                    supportingContent = { Text(stringResource(settings.suggestions.summaryRes())) },
                    colors = nestedItemColors(),
                ) {
                    Text(stringResource(R.string.settings_stickers_suggest))
                }
                ChoiceRow(
                    options = SuggestionChoices,
                    selected = settings.suggestions,
                    label = { stringResource(it.labelRes()) },
                    onSelect = { onEvent(StickersEvent.SetSuggestions(it)) },
                    contentPadding =
                        PaddingValues(
                            start = MaterialTheme.spacing.large,
                            end = MaterialTheme.spacing.large,
                            bottom = MaterialTheme.spacing.large,
                        ),
                )
            }
        }
    }
    item(key = LOOP, contentType = LOOP) {
        Box(Modifier.settingsListRow(this, style)) {
            SwitchItem(
                title = stringResource(R.string.settings_stickers_loop),
                summary = stringResource(R.string.settings_stickers_loop_summary),
                checked = settings.loop,
                shapes = ListItemDefaults.segmentedShapes(1, 2),
                onCheckedChange = { onEvent(StickersEvent.SetLoop(it)) },
            )
        }
    }
}

private val SuggestionChoices = StickerSuggestions.entries.toImmutableList()

private fun StickerSuggestions.labelRes(): Int =
    when (this) {
        StickerSuggestions.AllSets -> R.string.settings_stickers_suggest_all
        StickerSuggestions.MySets -> R.string.settings_stickers_suggest_mine
        StickerSuggestions.Off -> R.string.settings_stickers_suggest_off
    }

private fun StickerSuggestions.summaryRes(): Int =
    when (this) {
        StickerSuggestions.AllSets -> R.string.settings_stickers_suggest_all_summary
        StickerSuggestions.MySets -> R.string.settings_stickers_suggest_mine_summary
        StickerSuggestions.Off -> R.string.settings_stickers_suggest_off_summary
    }

private const val SETTINGS_HEADER = "settings-header"
private const val SUGGESTIONS = "settings-suggestions"
private const val LOOP = "settings-loop"
