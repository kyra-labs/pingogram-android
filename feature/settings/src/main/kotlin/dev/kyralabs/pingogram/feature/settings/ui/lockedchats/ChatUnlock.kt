package dev.kyralabs.pingogram.feature.settings.ui.lockedchats

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.passcode.BiometricButton
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeEntry
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeEntryTexts
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeEntryUiState
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeInput
import dev.kyralabs.pingogram.feature.settings.ui.passcode.PasscodeMessage
import dev.kyralabs.pingogram.feature.settings.ui.passcode.rememberBiometricPrompt
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps chat [chatId] behind the passcode while it is locked (`docs/adr/0029-locked-chats.md`): [content], the
 * conversation, while [hiddenChatIds] leaves it out, and in its place the passcode entry ([ChatUnlockRoute]) while it
 * holds it. Nothing until the locked chats are known, which they are from the app's start, so a chat opens without
 * waiting. The conversation's saved state is kept while the entry shows, and the chat opens where it was. `:app`
 * provides it to the conversation as `core:ui`'s `LocalLockedChatGate`.
 */
@Composable
fun LockedChatGate(
    hiddenChatIds: StateFlow<Set<Long>?>,
    chatId: Long,
    content: @Composable () -> Unit,
) {
    // Not with the lifecycle: chats locked again while the app was away must hide as it comes back, in its first frame.
    val hidden by hiddenChatIds.collectAsState()
    val saveableState = rememberSaveableStateHolder()
    val ids = hidden ?: return
    if (chatId in ids) {
        ChatUnlockRoute(title = stringResource(R.string.locked_chat_title))
    } else {
        saveableState.SaveableStateProvider(chatId) { content() }
    }
}

/**
 * The passcode entry before locked chats: hoists [ChatUnlockViewModel]. Each time it comes to the front after the chats
 * locked it shows the system's biometric prompt by itself, where biometric unlock is on; opening buzzes
 * ([HapticFeedbackType.Confirm]). "Cancel" goes back, as system back does.
 */
@Composable
internal fun ChatUnlockRoute(
    title: String,
    modifier: Modifier = Modifier,
    viewModel: ChatUnlockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val prompt = rememberBiometricPrompt(state.screenLock) { viewModel.onEvent(ChatUnlockEvent.BiometricSucceeded) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ChatUnlockEffect.Unlocked -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }
    val promptBiometrics by rememberUpdatedState(state.promptBiometrics)
    // Only once the entry is in front: the system refuses the prompt to an app in the background.
    LifecycleResumeEffect(state.promptBiometrics) {
        if (promptBiometrics) {
            viewModel.onEvent(ChatUnlockEvent.BiometricPromptShown)
            prompt.show()
        }
        onPauseOrDispose {}
    }
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    ChatUnlockScreen(
        title = title,
        state = state,
        input = viewModel.input,
        onEvent = viewModel::onEvent,
        onBiometricUnlock = prompt::show,
        onCancel = back?.let { { it.onBackPressed() } },
        modifier = modifier,
    )
}

/**
 * A [PasscodeEntry] titled [title] on `surface`, edge to edge, as the lock screen: "Cancel" at its bottom start (where
 * there is somewhere to go back to) and, where biometric unlock is on, the fingerprint button at its bottom end. No
 * "Forgot passcode?": the app's lock screen offers that, and locked chats open with the same passcode.
 */
@Suppress("LongParameterList") // The entry's title, state and input, and its three actions.
@Composable
internal fun ChatUnlockScreen(
    title: String,
    state: ChatUnlockUiState,
    input: PasscodeInput,
    onEvent: (ChatUnlockEvent) -> Unit,
    onBiometricUnlock: () -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        PasscodeEntry(
            texts =
                PasscodeEntryTexts(
                    title = title,
                    hint =
                        stringResource(
                            when (state.entry.type) {
                                PasscodeType.Pin -> R.string.locked_chats_hint_pin
                                PasscodeType.Password -> R.string.locked_chats_hint_password
                            },
                        ),
                    submit = stringResource(R.string.lock_unlock),
                ),
            state = state.entry,
            input = input,
            onSubmit = { onEvent(ChatUnlockEvent.Submit) },
            modifier =
                Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            secondaryAction =
                onCancel?.let { cancel ->
                    {
                        TextButton(onClick = cancel, shapes = ButtonDefaults.shapes()) {
                            Text(
                                stringResource(R.string.locked_chats_cancel),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                },
            extraAction =
                if (state.biometricUnlock) ({ BiometricButton(onBiometricUnlock, state.screenLock) }) else null,
        )
    }
}

@Composable
private fun ChatUnlockPreview(state: ChatUnlockUiState) {
    val input = remember { PasscodeInput().apply { repeat(2) { append('1', PasscodeType.Pin) } } }
    PingogramPreview {
        ChatUnlockScreen(
            title = stringResource(R.string.locked_chat_title),
            state = state,
            input = input,
            onEvent = {},
            onBiometricUnlock = {},
            onCancel = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ChatUnlockScreenPreview() {
    ChatUnlockPreview(ChatUnlockUiState(biometricUnlock = true))
}

@PingogramPreviews
@Composable
private fun ChatUnlockScreenWrongPreview() {
    ChatUnlockPreview(ChatUnlockUiState(entry = PasscodeEntryUiState(message = PasscodeMessage.Wrong, rejections = 1)))
}
