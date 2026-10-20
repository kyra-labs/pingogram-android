package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import dev.kyralabs.pingogram.feature.settings.R

// The rows of the Notifications page: the banner of blocked notifications, the defaults of a kind of chats, "Keep
// connected", and "Reset all notification settings" with its confirmation.

/**
 * Android shows none of Pingogram's notifications: a row in the error container that says so first on the page, and
 * that turns them on ([onClick]: the system's dialog, or Android's notification settings).
 */
@Composable
internal fun NotificationsOffItem(
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = stringResource(R.string.settings_notifications_off),
        shapes = shapes,
        supportingContent = { Text(stringResource(R.string.settings_notifications_off_summary)) },
        onClick = onClick,
        colors = warningItemColors(),
    )
}

/** The colors of a row that warns: `onErrorContainer` on `errorContainer` (`docs/DESIGN_SYSTEM.md` §2.2). */
@Composable
private fun warningItemColors(): ListItemColors {
    val colors = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = colors.errorContainer,
        contentColor = colors.onErrorContainer,
        supportingContentColor = colors.onErrorContainer,
    )
}

/**
 * The defaults of [scope]'s chats under its name: notifications on or off, message previews, mentions (groups) and
 * pinned messages (groups and channels) as switches, then "Sound and vibration", which opens the scope's notification
 * channel in Android's settings (noting when the user turned it off there, [channelBlocked]), and "Exceptions"
 * ([onOpenExceptions]). Each switch sends [settings] with its change.
 */
@Composable
internal fun ScopeGroup(
    scope: NotificationScope,
    settings: ScopeNotificationSettings,
    channelBlocked: Boolean,
    onEvent: (NotificationSettingsEvent) -> Unit,
    onOpenExceptions: () -> Unit,
) {
    val change = { changed: ScopeNotificationSettings ->
        onEvent(NotificationSettingsEvent.ChangeScope(scope, changed))
    }
    SettingsGroup(title = stringResource(scope.titleRes())) {
        scopeSwitches(scope, settings, change)
        item(key = SOUND) { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_notifications_sound),
                shapes = shapes,
                supportingContent = {
                    if (channelBlocked) {
                        Text(
                            text = stringResource(R.string.settings_notifications_sound_blocked),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(stringResource(R.string.settings_notifications_sound_summary))
                    }
                },
                onClick = { onEvent(NotificationSettingsEvent.OpenChannelSettings(scope)) },
            )
        }
        item(key = EXCEPTIONS) { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_notifications_exceptions),
                shapes = shapes,
                supportingContent = { Text(stringResource(R.string.settings_notifications_exceptions_summary)) },
                onClick = onOpenExceptions,
            )
        }
    }
}

/**
 * The switches of [scope]'s group: notifications on or off, message previews, mentions (groups) and pinned messages
 * (groups and channels), each calling [change] with [settings] changed.
 */
private fun SettingsGroupScope.scopeSwitches(
    scope: NotificationScope,
    settings: ScopeNotificationSettings,
    change: (ScopeNotificationSettings) -> Unit,
) {
    item(key = NOTIFY) { shapes ->
        SwitchItem(
            title = stringResource(R.string.settings_notifications_notify),
            summary = stringResource(scope.notifySummaryRes()),
            checked = !settings.isMuted,
            shapes = shapes,
            onCheckedChange = { change(settings.copy(isMuted = !it)) },
        )
    }
    item(key = PREVIEW) { shapes ->
        SwitchItem(
            title = stringResource(R.string.settings_notifications_preview),
            summary = stringResource(R.string.settings_notifications_preview_summary),
            checked = settings.showPreview,
            shapes = shapes,
            onCheckedChange = { change(settings.copy(showPreview = it)) },
        )
    }
    if (scope == NotificationScope.GroupChats) {
        item(key = MENTIONS) { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_notifications_mentions),
                summary = stringResource(R.string.settings_notifications_mentions_summary),
                checked = settings.mentions,
                shapes = shapes,
                onCheckedChange = { change(settings.copy(mentions = it)) },
            )
        }
    }
    if (scope != NotificationScope.PrivateChats) {
        item(key = PINNED) { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_notifications_pinned),
                summary = stringResource(R.string.settings_notifications_pinned_summary),
                checked = settings.pinnedMessages,
                shapes = shapes,
                onCheckedChange = { change(settings.copy(pinnedMessages = it)) },
            )
        }
    }
}

/**
 * "Keep connected" (`foss` only), with what it costs right under the switch: the battery a connection kept open uses,
 * and the notification Android shows while it runs (`docs/PERFORMANCE.md` §6).
 */
@Composable
internal fun KeepConnectedGroup(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_notifications_section_background)) {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_keep_connected),
                summary = stringResource(R.string.settings_keep_connected_summary),
                checked = enabled,
                shapes = shapes,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

/** "Reset all notification settings": destructive, so in the error role; disabled with progress while resetting. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ResetNotificationsItem(
    isResetting: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val resetting = stringResource(R.string.settings_notifications_resetting)
    SettingsRow(
        title = stringResource(R.string.settings_notifications_reset),
        shapes = shapes,
        supportingContent = { Text(stringResource(R.string.settings_notifications_reset_summary)) },
        trailingContent =
            if (isResetting) {
                { LoadingIndicator(Modifier.semantics { contentDescription = resetting }) }
            } else {
                null
            },
        onClick = onClick,
        enabled = !isResetting,
        colors = destructiveItemColors(),
    )
}

/** The destructive confirmation ([DestructiveDialog]): every chat's own settings and every default go. */
@Composable
internal fun ResetNotificationsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveDialog(
        icon = null,
        texts =
            DestructiveDialogTexts(
                title = stringResource(R.string.settings_notifications_reset_dialog_title),
                text = stringResource(R.string.settings_notifications_reset_dialog_text),
                confirm = stringResource(R.string.settings_notifications_reset_dialog_confirm),
            ),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The heading of [this] scope's group, and the title of its exceptions page's chats. */
@StringRes
internal fun NotificationScope.titleRes(): Int =
    when (this) {
        NotificationScope.PrivateChats -> R.string.settings_notifications_private_chats
        NotificationScope.GroupChats -> R.string.settings_notifications_groups
        NotificationScope.ChannelChats -> R.string.settings_notifications_channels
    }

@StringRes
private fun NotificationScope.notifySummaryRes(): Int =
    when (this) {
        NotificationScope.PrivateChats -> R.string.settings_notifications_notify_private_chats
        NotificationScope.GroupChats -> R.string.settings_notifications_notify_groups
        NotificationScope.ChannelChats -> R.string.settings_notifications_notify_channels
    }

private const val NOTIFY = "notify"
private const val PREVIEW = "preview"
private const val MENTIONS = "mentions"
private const val PINNED = "pinned"
private const val SOUND = "sound"
private const val EXCEPTIONS = "exceptions"
