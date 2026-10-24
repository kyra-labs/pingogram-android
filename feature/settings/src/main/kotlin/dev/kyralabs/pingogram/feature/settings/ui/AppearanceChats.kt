package dev.kyralabs.pingogram.feature.settings.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ChatAppearance
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.systemContrastOrNull
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * The "Chats" group: Chat appearance, which Enter sends on a hardware and on the on-screen keyboard, and raise to
 * listen.
 */
@Composable
internal fun ChatsGroup(
    chat: ChatAppearance,
    chats: ChatsSettings,
    onEvent: (AppearanceEvent) -> Unit,
    onOpenChatAppearance: () -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_chats)) {
        item { shapes -> ChatAppearanceItem(chat, shapes, onOpenChatAppearance) }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_enter_sends_hardware),
                summary =
                    stringResource(
                        if (chats.sendKeys.hardwareKeyboard) {
                            R.string.settings_enter_sends_hardware_on
                        } else {
                            R.string.settings_enter_sends_hardware_off
                        },
                    ),
                checked = chats.sendKeys.hardwareKeyboard,
                shapes = shapes,
                onCheckedChange = {
                    onEvent(AppearanceEvent.SetSendKeys(chats.sendKeys.copy(hardwareKeyboard = it)))
                },
            )
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_enter_sends_on_screen),
                summary = stringResource(R.string.settings_enter_sends_on_screen_summary),
                checked = chats.sendKeys.onScreenKeyboard,
                shapes = shapes,
                onCheckedChange = {
                    onEvent(AppearanceEvent.SetSendKeys(chats.sendKeys.copy(onScreenKeyboard = it)))
                },
            )
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_raise_to_listen),
                summary = stringResource(R.string.settings_raise_to_listen_summary),
                checked = chats.raiseToListen.enabled,
                shapes = shapes,
                onCheckedChange = { onEvent(AppearanceEvent.SetRaiseToListen(it)) },
            )
        }
        if (chats.raiseToListen.enabled) {
            item { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_pause_when_lowered),
                    summary = stringResource(R.string.settings_pause_when_lowered_summary),
                    checked = chats.raiseToListen.pauseWhenLowered,
                    shapes = shapes,
                    onCheckedChange = { onEvent(AppearanceEvent.SetPauseWhenLowered(it)) },
                )
            }
        }
    }
}

/** The "Chats" group's settings beside the appearance: which Enter sends a message, and raise to listen. */
internal class ChatsSettings(
    val sendKeys: SendKeys,
    val raiseToListen: RaiseToListenSettings,
)

/** The row that opens the Chat appearance page: its name over the text size, the corners and the wallpaper. */
@Composable
private fun ChatAppearanceItem(
    chat: ChatAppearance,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val percent =
        remember(chat.textScale, locale) {
            NumberFormat.getPercentInstance(locale).format(chat.textScale.toDouble())
        }
    SettingsRow(
        title = stringResource(R.string.settings_chat_appearance),
        shapes = shapes,
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_chat_appearance_summary,
                    percent,
                    chat.bubbleCorners.label(),
                    chat.wallpaper.label(),
                ),
            )
        },
        onClick = onClick,
    )
}
