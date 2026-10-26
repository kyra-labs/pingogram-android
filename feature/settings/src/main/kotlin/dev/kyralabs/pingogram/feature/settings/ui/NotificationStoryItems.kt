package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.StoryNotifications
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.toImmutableList

/**
 * The private chats' story notifications (`docs/audit/feature-gaps-octogram.md` P3): "New stories" over what the choice
 * does, then Automatic, All and None as a [ChoiceRow]; while any notify, "Name who posted" as a switch. Each calls
 * [change] with [settings] changed, as the scope's other switches do.
 */
internal fun SettingsGroupScope.storyItems(
    settings: ScopeNotificationSettings,
    change: (ScopeNotificationSettings) -> Unit,
) {
    item(key = STORIES) { shapes ->
        SettingsSegment(shapes) {
            SegmentedListItem(
                shapes = ListItemDefaults.shapes(),
                supportingContent = { Text(stringResource(settings.stories.summaryRes())) },
                colors = nestedItemColors(),
            ) {
                Text(stringResource(R.string.settings_notifications_stories))
            }
            ChoiceRow(
                options = StoryChoices,
                selected = settings.stories,
                label = { stringResource(it.labelRes()) },
                onSelect = { change(settings.copy(stories = it)) },
                contentPadding =
                    PaddingValues(
                        start = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.large,
                    ),
            )
        }
    }
    if (settings.stories != StoryNotifications.None) {
        item(key = STORY_POSTER) { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_notifications_story_poster),
                summary = stringResource(R.string.settings_notifications_story_poster_summary),
                checked = settings.showStoryPoster,
                shapes = shapes,
                onCheckedChange = { change(settings.copy(showStoryPoster = it)) },
            )
        }
    }
}

private val StoryChoices = StoryNotifications.entries.toImmutableList()

private fun StoryNotifications.labelRes(): Int =
    when (this) {
        StoryNotifications.Automatic -> R.string.settings_notifications_stories_automatic
        StoryNotifications.All -> R.string.settings_notifications_stories_all
        StoryNotifications.None -> R.string.settings_notifications_stories_none
    }

private fun StoryNotifications.summaryRes(): Int =
    when (this) {
        StoryNotifications.Automatic -> R.string.settings_notifications_stories_automatic_summary
        StoryNotifications.All -> R.string.settings_notifications_stories_all_summary
        StoryNotifications.None -> R.string.settings_notifications_stories_none_summary
    }

private const val STORIES = "stories"
private const val STORY_POSTER = "storyPoster"
