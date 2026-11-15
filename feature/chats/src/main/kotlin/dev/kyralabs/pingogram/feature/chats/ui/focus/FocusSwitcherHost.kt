package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** [FocusSwitcherSheet] while the switcher is open; [state] is read here alone. */
@Composable
internal fun FocusSwitcherHost(
    state: State<FocusSwitchUiState>,
    onEvent: (FocusSwitchEvent) -> Unit,
    onManage: () -> Unit,
) {
    val open by remember(state) { derivedStateOf { state.value.switcherOpen } }
    if (open) {
        FocusSwitcherSheet(
            state = state.value,
            onEvent = onEvent,
            onManage = {
                onEvent(FocusSwitchEvent.Close)
                onManage()
            },
        )
    }
}

/** Shows why a Focus change was not saved, in the chat list's snackbar. */
@Composable
internal fun FocusSwitchEffects(
    effects: Flow<FocusSwitchEffect>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is FocusSwitchEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
}
