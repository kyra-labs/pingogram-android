package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The members page of the route's group or channel: hoists its [ChatMembersViewModel] and handles its effects. A
 * member's profile opens through [onOpenProfile], a chat (a removed channel) through [onOpenChat], the rights page of a
 * member through [onOpenRights]; what was done and errors are snackbars, the newest replacing the one shown.
 */
@Composable
internal fun ChatMembersRoute(
    onBack: () -> Unit,
    onOpenProfile: (userId: Long) -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenRights: (userId: Long, restrict: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatMembersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentOnOpenProfile by rememberUpdatedState(onOpenProfile)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    val currentOnOpenRights by rememberUpdatedState(onOpenRights)
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is ChatMembersEffect.OpenProfile -> {
                        currentOnOpenProfile(effect.userId)
                        ""
                    }

                    is ChatMembersEffect.OpenChat -> {
                        currentOnOpenChat(effect.chatId)
                        ""
                    }

                    is ChatMembersEffect.OpenRights -> {
                        currentOnOpenRights(effect.userId, effect.restrict)
                        ""
                    }

                    is ChatMembersEffect.Done -> {
                        val name = effect.name.resolve(resources).bidiIsolated()
                        resources.getString(effect.text, name).bidiIsolated()
                    }

                    is ChatMembersEffect.ShowError -> {
                        errorMessage(resources, effect.error, showDetails)
                    }
                }
            if (message.isNotEmpty()) {
                snackbar?.cancel()
                snackbar = launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
    ChatMembersScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
