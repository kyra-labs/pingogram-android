package dev.kyralabs.pingogram.feature.settings.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.notifications.PingogramChannel
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.component.LocalIsDemo
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

// The experimental Notifications page's parts (docs/DESIGN_SYSTEM.md §13.10): the banner while notifications are off
// and the picker of the kind of chat whose defaults show.

/**
 * While Android shows none of Pingogram's notifications: in the experimental look a banner ([SettingsBannerCard])
 * with "Turn on", in the standard look the warning row ([NotificationsOffItem]); both call [onTurnOn].
 */
@Composable
internal fun NotificationsOff(onTurnOn: () -> Unit) {
    if (LocalExperimentalUi.current) {
        SettingsBannerCard(
            icon = UiR.drawable.ic_notifications_off,
            title = stringResource(R.string.settings_notifications_off),
            summary = stringResource(R.string.settings_notifications_off_banner),
            button = stringResource(R.string.settings_notifications_turn_on),
            onClick = onTurnOn,
        )
    } else {
        SettingsGroup {
            item { shapes -> NotificationsOffItem(shapes, onClick = onTurnOn) }
        }
    }
}

/** "Show settings for": private chats, groups and channels as connected buttons, which pick the group shown below. */
@Composable
internal fun ScopePicker(
    shown: NotificationScope,
    onSelect: (NotificationScope) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_notifications_show_for)) {
        item { shapes ->
            SettingsSegment(shapes) {
                ChoiceRow(
                    options = NotificationScopes,
                    selected = shown,
                    label = { stringResource(it.shortTitleRes()) },
                    onSelect = onSelect,
                    contentPadding = PaddingValues(MaterialTheme.spacing.large),
                )
            }
        }
    }
}

private val NotificationScopes = NotificationScope.entries.toImmutableList()

private fun NotificationScope.shortTitleRes(): Int =
    when (this) {
        NotificationScope.PrivateChats -> R.string.settings_notifications_scope_private
        NotificationScope.GroupChats -> R.string.settings_notifications_scope_groups
        NotificationScope.ChannelChats -> R.string.settings_notifications_scope_channels
    }
