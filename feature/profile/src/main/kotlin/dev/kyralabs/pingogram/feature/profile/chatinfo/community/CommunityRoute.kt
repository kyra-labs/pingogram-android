package dev.kyralabs.pingogram.feature.profile.chatinfo.community

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
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The page of the route's community: hoists its [CommunityViewModel] and handles its effects. A chat opens through
 * [onOpenChat]; once the community is deleted, [onBack] closes the page; notices and errors are snackbars, the newest
 * replacing the one shown.
 */
@Composable
internal fun CommunityScreenRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is CommunityEffect.OpenChat -> {
                        currentOnOpenChat(effect.chatId)
                        ""
                    }

                    CommunityEffect.Deleted -> {
                        currentOnBack()
                        ""
                    }

                    is CommunityEffect.Notice -> {
                        resources.getString(effect.text)
                    }

                    is CommunityEffect.CountNotice -> {
                        resources.getQuantityString(effect.text, effect.count, effect.count)
                    }

                    is CommunityEffect.ShowError -> {
                        errorMessage(resources, effect.error, showDetails)
                    }
                }
            if (message.isNotEmpty()) {
                snackbar?.cancel()
                snackbar = launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
    CommunityScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
