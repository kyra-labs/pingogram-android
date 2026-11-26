package dev.kyralabs.pingogram.feature.settings.ui.chats

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettings
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ChoiceRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroupScope
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.nestedItemColors
import kotlinx.collections.immutable.toImmutableList

/**
 * Unanswered's rows (`REQUIREMENTS.md` FR-X22): "Unanswered button" for the Chats tab's header, then "Unanswered
 * after" with its days as [ChoiceRow]'s connected buttons, dimmed while the button is off: only it opens the page.
 */
internal fun SettingsGroupScope.unansweredItems(
    settings: UnansweredSettings,
    onEvent: (ChatsPageEvent) -> Unit,
) {
    item(key = BUTTON) { shapes ->
        SwitchItem(
            title = stringResource(R.string.settings_unanswered_button),
            summary = stringResource(R.string.settings_unanswered_button_summary),
            checked = settings.showButton,
            shapes = shapes,
            onCheckedChange = { onEvent(ChatsPageEvent.SetShowUnansweredButton(it)) },
        )
    }
    item(key = AFTER) { shapes ->
        SettingsSegment(shapes) {
            SettingsListItem(
                shapes = ListItemDefaults.shapes(),
                enabled = settings.showButton,
                supportingContent = { Text(stringResource(R.string.settings_unanswered_after_summary)) },
                colors = nestedItemColors(),
            ) {
                Text(stringResource(R.string.settings_unanswered_after))
            }
            val spacing = MaterialTheme.spacing
            ChoiceRow(
                options = DayChoices,
                selected = settings.afterDays,
                label = { pluralStringResource(R.plurals.settings_unanswered_days, it, it) },
                onSelect = { onEvent(ChatsPageEvent.SetUnansweredAfterDays(it)) },
                contentPadding = PaddingValues(start = spacing.large, end = spacing.large, bottom = spacing.large),
                enabled = settings.showButton,
            )
        }
    }
}

private const val BUTTON = "unanswered_button"
private const val AFTER = "unanswered_after"

private val DayChoices = UnansweredSettings.DAYS.toImmutableList()
