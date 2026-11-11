package dev.kyralabs.pingogram.feature.settings.ui.profilecolor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import kotlinx.coroutines.launch

/** "Your color": hoists [ProfileColorViewModel]; once Telegram took the color, the page closes ([onBack]). */
@Composable
internal fun ProfileColorRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileColorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showDetails = isDebuggable()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ProfileColorEffect.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnBack()
                }

                is ProfileColorEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showDetails)) }
                }
            }
        }
    }
    ProfileColorScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}
