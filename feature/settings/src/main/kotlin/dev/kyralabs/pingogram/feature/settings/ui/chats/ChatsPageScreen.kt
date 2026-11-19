package dev.kyralabs.pingogram.feature.settings.ui.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch

/** The Chats page: hoists [ChatsPageViewModel] and shows in snackbars the changes that could not be saved. */
@Composable
internal fun ChatsPageRoute(
    pages: ChatsPagePages,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsPageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is ChatsPageEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    ChatsPageScreen(state, viewModel::onEvent, onBack, modifier, pages, snackbarHostState)
}

/**
 * Settings → Chats (`REQUIREMENTS.md` FR-S2, `docs/DESIGN_SYSTEM.md` §13.10, owner decision 2026-11-19): how chats
 * behave, while Appearance keeps how they look. "Messages" (Double-tap to react, Snippets, which Enter sends),
 * "Voice messages" (transcription, raise to listen, skipping silences) and "Chat list" (Swipe actions, Chats shown
 * first); rows that open a page ([pages]) end in a chevron.
 */
@Composable
internal fun ChatsPageScreen(
    state: ChatsPageUiState,
    onEvent: (ChatsPageEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pages: ChatsPagePages = ChatsPagePages(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_chats),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            ChatsPageUiState.Loading -> {
                val loading = stringResource(R.string.settings_appearance_loading)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
                }
            }

            is ChatsPageUiState.Content -> {
                MessagesGroup(state.sendKeys, onEvent, pages)
                VoiceMessagesGroup(state.raiseToListen, state.skipSilence, onEvent, pages)
                ChatListBehaviorGroup(state.chatList, pages)
            }
        }
    }
}

/** What opens the pages above the Chats page. */
internal class ChatsPagePages(
    val onOpenQuickReaction: () -> Unit = {},
    val onOpenSnippets: () -> Unit = {},
    val onOpenTranscription: () -> Unit = {},
    val onOpenSwipeActions: () -> Unit = {},
    val onOpenShownFirst: () -> Unit = {},
    val onOpenRaiseToListen: () -> Unit = {},
)

@PingogramPreviews
@Composable
private fun ChatsPageScreenPreview() {
    PingogramPreview { ChatsPageScreen(state = ChatsPageUiState.Content(), onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ChatsPageScreenChangedPreview() {
    PingogramPreview {
        ChatsPageScreen(
            state =
                ChatsPageUiState.Content(
                    sendKeys = SendKeys(hardwareKeyboard = false, onScreenKeyboard = true),
                    raiseToListen = RaiseToListenSettings(enabled = false),
                    skipSilence = true,
                ),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ChatsPageScreenLoadingPreview() {
    PingogramPreview { ChatsPageScreen(state = ChatsPageUiState.Loading, onEvent = {}, onBack = {}) }
}
