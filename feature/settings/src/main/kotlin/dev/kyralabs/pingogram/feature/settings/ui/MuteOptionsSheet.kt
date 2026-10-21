package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.ChatMute
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The mute options of an exception's chat ([item]), in a [ModalBottomSheet]: the chat and whether it notifies as its
 * heading, then mute for an hour, 8 hours or a day, mute forever, unmute (unless it is unmuted) and use the default.
 * Choosing one hides the sheet on its spring, then sends its change; dragging it down, the scrim and the back gesture
 * dismiss it ([NotificationExceptionsEvent.DismissMuteOptions]). It scrolls where the window is too short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MuteOptionsSheet(
    item: NotificationExceptionItem,
    onEvent: (NotificationExceptionsEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val choose: (MuteChange) -> Unit =
        remember(sheetState, scope, item.chatId) {
            { change ->
                scope
                    .launch { sheetState.hide() }
                    .invokeOnCompletion { currentOnEvent(NotificationExceptionsEvent.Change(item.chatId, change)) }
            }
        }
    ModalBottomSheet(
        onDismissRequest = { currentOnEvent(NotificationExceptionsEvent.DismissMuteOptions) },
        sheetState = sheetState,
    ) {
        MuteOptionsContent(item, choose)
    }
}

/** The sheet's heading and options. */
@Composable
internal fun MuteOptionsContent(
    item: NotificationExceptionItem,
    choose: (MuteChange) -> Unit,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(bottom = MaterialTheme.spacing.large),
    ) {
        ListItem(
            leadingContent = {
                Avatar(
                    photo = item.photo,
                    name = item.title,
                    id = item.chatId,
                    size = MaterialTheme.sizes.avatarSmall,
                )
            },
            supportingContent = { Text(item.status.resolve()) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(
                text = item.title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium.userContent,
            )
        }
        for (hours in MUTE_HOURS) {
            MuteOption(
                label = pluralStringResource(R.plurals.settings_mute_for_hours, hours, hours),
                icon = UiR.drawable.ic_schedule,
                onClick = { choose(MuteChange.MuteFor(hours * SECONDS_PER_HOUR)) },
            )
        }
        MuteOption(
            label = pluralStringResource(R.plurals.settings_mute_for_days, 1, 1),
            icon = UiR.drawable.ic_schedule,
            onClick = { choose(MuteChange.MuteFor(SECONDS_PER_DAY)) },
        )
        MuteOption(
            label = stringResource(R.string.settings_mute_forever),
            icon = UiR.drawable.ic_notifications_off,
            onClick = { choose(MuteChange.MuteForever) },
        )
        if (item.mute != ChatMute.Unmuted) {
            MuteOption(
                label = stringResource(R.string.settings_unmute),
                icon = UiR.drawable.ic_notifications,
                onClick = { choose(MuteChange.Unmute) },
            )
        }
        MuteOption(
            label = stringResource(R.string.settings_mute_use_default),
            icon = UiR.drawable.ic_refresh,
            onClick = { choose(MuteChange.UseDefault) },
        )
    }
}

/** One option of the sheet: its outlined [icon] and [label]. */
@Composable
private fun MuteOption(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(label)
    }
}

/** The hours a chat can be muted for, besides a day and forever. */
private val MUTE_HOURS = listOf(1, 8)
private const val SECONDS_PER_HOUR = 60 * 60
private const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR

@PingogramPreviews
@Composable
private fun MuteOptionsContentPreview() {
    PingogramPreview { MuteOptionsContent(item = SettingsPreviewData.notificationExceptionItems.first(), choose = {}) }
}
