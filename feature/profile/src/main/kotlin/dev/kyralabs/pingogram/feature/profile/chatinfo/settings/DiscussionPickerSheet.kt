package dev.kyralabs.pingogram.feature.profile.chatinfo.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R

/**
 * The groups a channel may discuss in (`docs/DESIGN_SYSTEM.md` §6, "Group and channel settings"): a
 * `ModalBottomSheet` with its title, what linking does, and the groups as rows inset like the chat list's; a tap links
 * one. Loading is a delayed indicator, a failure its error, and none a note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiscussionPickerSheet(
    picker: DiscussionPicker,
    onEvent: (ChatSettingsEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(ChatSettingsEvent.CloseDiscussionPicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        DiscussionPickerContent(picker, onEvent)
    }
}

@Composable
internal fun DiscussionPickerContent(
    picker: DiscussionPicker,
    onEvent: (ChatSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier.fillMaxWidth().padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text(
            text = stringResource(R.string.chat_settings_picker_title),
            modifier = Modifier.padding(horizontal = spacing.large).semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text = stringResource(R.string.chat_settings_picker_text),
            modifier = Modifier.padding(horizontal = spacing.large),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        when (picker) {
            DiscussionPicker.Loading -> {
                val description = stringResource(R.string.chat_settings_picker_loading)
                Box(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.sizes.illustrationMaxSize), Alignment.Center) {
                    DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
                }
            }

            is DiscussionPicker.Failed -> {
                Note(
                    errorMessage(LocalResources.current, picker.error, isDebuggable()),
                    MaterialTheme.colorScheme.error,
                )
            }

            is DiscussionPicker.Groups -> {
                if (picker.groups.isEmpty()) {
                    Note(stringResource(R.string.chat_settings_picker_none), MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    GroupList(picker, onEvent)
                }
            }
        }
    }
}

@Composable
private fun GroupList(
    picker: DiscussionPicker.Groups,
    onEvent: (ChatSettingsEvent) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(picker.groups, key = { it.chatId }) { group ->
            ListItem(
                headlineContent = { Text(group.title, style = LocalTextStyle.current.userContent) },
                modifier =
                    Modifier
                        .padding(horizontal = MaterialTheme.spacing.small)
                        .clickable { onEvent(ChatSettingsEvent.LinkDiscussion(group.chatId)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun Note(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
}
