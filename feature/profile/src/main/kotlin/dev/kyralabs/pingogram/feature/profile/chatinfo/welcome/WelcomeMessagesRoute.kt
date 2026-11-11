package dev.kyralabs.pingogram.feature.profile.chatinfo.welcome

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The welcome message page of the route's group: hoists its [WelcomeMessagesViewModel], opens the system photo picker
 * (images only, no permission) for the editor, and shows notices and errors as snackbars, the newest replacing the one
 * shown; a saved message gives the `Confirm` haptic.
 */
@Composable
internal fun WelcomeMessagesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeMessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showDetails = isDebuggable()
    val photoPicker =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            if (uri != null) viewModel.onEvent(WelcomeMessagesEvent.PhotoPicked(uri.toString()))
        }
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    WelcomeMessagesEffect.PickPhoto -> {
                        photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        ""
                    }

                    is WelcomeMessagesEffect.Notice -> {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        resources.getString(effect.text)
                    }

                    is WelcomeMessagesEffect.ShowError -> {
                        errorMessage(resources, effect.error, showDetails)
                    }
                }
            if (message.isNotEmpty()) {
                snackbar?.cancel()
                snackbar = launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
    WelcomeMessagesScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
