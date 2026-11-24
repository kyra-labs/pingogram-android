package dev.kyralabs.pingogram.feature.settings.ui.chats

import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSplitRow
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.chatlist.shortLabelRes

/**
 * "Messages": the rows of Double-tap to react and of Snippets ([pages]), which Enter sends on a keyboard and on
 * the screen keyboard, whether messages go without sound unless chosen (`REQUIREMENTS.md` FR-X11), whether a bot
 * command asks before it goes (FR-X12), and whether the message box's menu offers Undo and Redo (FR-X13).
 */
@Composable
internal fun MessagesGroup(
    state: ChatsPageUiState.Content,
    onEvent: (ChatsPageEvent) -> Unit,
    pages: ChatsPagePages,
) {
    val sendKeys = state.sendKeys
    SettingsGroup(title = stringResource(R.string.settings_chat_section_messages)) {
        item { shapes ->
            PageRow(
                R.string.settings_double_tap,
                R.string.settings_double_tap_summary,
                shapes,
                pages.onOpenQuickReaction,
            )
        }
        item { shapes ->
            PageRow(R.string.settings_snippets, R.string.settings_snippets_summary, shapes, pages.onOpenSnippets)
        }
        item { shapes -> HardwareEnterItem(sendKeys, shapes, onEvent) }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_enter_sends_on_screen),
                summary = stringResource(R.string.settings_enter_sends_on_screen_summary),
                checked = sendKeys.onScreenKeyboard,
                shapes = shapes,
                onCheckedChange = { onEvent(ChatsPageEvent.SetSendKeys(sendKeys.copy(onScreenKeyboard = it))) },
            )
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_send_silently),
                summary = stringResource(R.string.settings_send_silently_summary),
                checked = state.sendsSilently,
                shapes = shapes,
                onCheckedChange = { onEvent(ChatsPageEvent.SetSendsSilently(it)) },
            )
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_confirm_bot_commands),
                summary = stringResource(R.string.settings_confirm_bot_commands_summary),
                checked = state.confirmsBotCommands,
                shapes = shapes,
                onCheckedChange = { onEvent(ChatsPageEvent.SetConfirmsBotCommands(it)) },
            )
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_undo_redo),
                summary = stringResource(R.string.settings_undo_redo_summary),
                checked = state.showsUndoRedo,
                shapes = shapes,
                onCheckedChange = { onEvent(ChatsPageEvent.SetShowsUndoRedo(it)) },
            )
        }
    }
}

/**
 * "Voice messages" ([state]): voice message transcription, a split row ([SettingsSplitRow]) whose switch is
 * "Transcribe automatically" (a plain row that opens its page where the phone cannot transcribe); raise to listen, a
 * split row whose text opens its page, where "Pause when lowered" is; and skipping voice messages' silences.
 */
@Composable
internal fun VoiceMessagesGroup(
    state: ChatsPageUiState.Content,
    onEvent: (ChatsPageEvent) -> Unit,
    pages: ChatsPagePages,
) {
    val raiseToListen = state.raiseToListen
    val transcribeAutomatically = state.transcribeAutomatically
    SettingsGroup(title = stringResource(R.string.settings_section_voice_messages)) {
        item { shapes ->
            if (transcribeAutomatically != null) {
                SettingsSplitRow(
                    title = stringResource(R.string.settings_transcription),
                    summary = stringResource(R.string.settings_transcription_summary),
                    checked = transcribeAutomatically,
                    shapes = shapes,
                    onOpen = pages.onOpenTranscription,
                    onCheckedChange = { onEvent(ChatsPageEvent.SetTranscribeAutomatically(it)) },
                    switchLabel = stringResource(R.string.settings_transcription_automatic),
                )
            } else {
                PageRow(
                    R.string.settings_transcription,
                    R.string.settings_transcription_summary,
                    shapes,
                    pages.onOpenTranscription,
                )
            }
        }
        item { shapes ->
            SettingsSplitRow(
                title = stringResource(R.string.settings_raise_to_listen),
                summary = stringResource(R.string.settings_raise_to_listen_summary),
                checked = raiseToListen.enabled,
                shapes = shapes,
                onOpen = pages.onOpenRaiseToListen,
                onCheckedChange = { onEvent(ChatsPageEvent.SetRaiseToListen(it)) },
            )
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_skip_silence),
                summary = stringResource(R.string.settings_skip_silence_summary),
                checked = state.skipSilence,
                shapes = shapes,
                onCheckedChange = { onEvent(ChatsPageEvent.SetSkipSilence(it)) },
            )
        }
    }
}

/** "Chat list": the rows of Swipe actions, with the two swipes' actions as its summary, and of Chats shown first. */
@Composable
internal fun ChatListBehaviorGroup(
    chatList: ChatListSettings,
    onEvent: (ChatsPageEvent) -> Unit,
    pages: ChatsPagePages,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_chat_list)) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_swipe_actions),
                shapes = shapes,
                supportingContent = { Text(swipeActionsSummary(chatList)) },
                onClick = pages.onOpenSwipeActions,
                opensPage = true,
            )
        }
        item { shapes ->
            PageRow(R.string.shown_first_title, R.string.shown_first_summary, shapes, pages.onOpenShownFirst)
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_drafts_button),
                summary = stringResource(R.string.settings_drafts_button_summary),
                checked = chatList.showDraftsButton,
                shapes = shapes,
                onCheckedChange = { onEvent(ChatsPageEvent.SetShowDraftsButton(it)) },
            )
        }
    }
}

/** A row that opens another page: its title over its summary, ending in a chevron. */
@Composable
private fun PageRow(
    title: Int,
    summary: Int,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = stringResource(title),
        shapes = shapes,
        supportingContent = { Text(stringResource(summary)) },
        onClick = onClick,
        opensPage = true,
    )
}

/** "Send with Enter on a keyboard", its summary saying which keys send and which start a line. */
@Composable
private fun HardwareEnterItem(
    sendKeys: SendKeys,
    shapes: ListItemShapes,
    onEvent: (ChatsPageEvent) -> Unit,
) {
    SwitchItem(
        title = stringResource(R.string.settings_enter_sends_hardware),
        summary =
            stringResource(
                if (sendKeys.hardwareKeyboard) {
                    R.string.settings_enter_sends_hardware_on
                } else {
                    R.string.settings_enter_sends_hardware_off
                },
            ),
        checked = sendKeys.hardwareKeyboard,
        shapes = shapes,
        onCheckedChange = { onEvent(ChatsPageEvent.SetSendKeys(sendKeys.copy(hardwareKeyboard = it))) },
    )
}

/** "Right: Read · Left: Archive", the sides as the layout's direction puts them. */
@Composable
private fun swipeActionsSummary(chatList: ChatListSettings): String {
    if (chatList.swipeChangesFolder) return stringResource(R.string.settings_swipe_actions_summary_folders)
    return stringResource(
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
            R.string.settings_swipe_actions_summary
        } else {
            R.string.settings_swipe_actions_summary_rtl
        },
        stringResource(chatList.startSwipe.shortLabelRes()),
        stringResource(chatList.endSwipe.shortLabelRes()),
    )
}
