package dev.kyralabs.pingogram.feature.calls.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.LocalCallStarter
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.feature.calls.ui.GroupCallActivity
import kotlinx.coroutines.launch

/**
 * Recent calls: hoists [RecentCallsViewModel], calls back through the app's call starter ([LocalCallStarter], which
 * opens the call screen) and shows the result of deleting in a snackbar. [onBack] leaves the page.
 */
@Composable
internal fun RecentCallsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecentCallsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val calls = LocalCallStarter.current
    val resources = LocalResources.current
    val context = LocalContext.current
    val showDetails = isDebuggable()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RecentCallsEffect.Call -> {
                    if (effect.isVideo) calls?.videoCall(effect.userId) else calls?.call(effect.userId)
                }

                is RecentCallsEffect.JoinGroupCall -> {
                    GroupCallActivity.openInvitation(context, effect.chatId, effect.messageId)
                }

                RecentCallsEffect.GroupCallEnded -> {
                    launch {
                        snackbarHostState.showSnackbar(
                            resources.getString(R.string.recent_calls_group_call_ended),
                        )
                    }
                }

                RecentCallsEffect.Deleted -> {
                    launch { snackbarHostState.showSnackbar(resources.getString(R.string.recent_calls_deleted)) }
                }

                is RecentCallsEffect.DeleteFailed -> {
                    val message = errorMessage(resources, effect.error, showDetails)
                    launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    }
    RecentCallsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
