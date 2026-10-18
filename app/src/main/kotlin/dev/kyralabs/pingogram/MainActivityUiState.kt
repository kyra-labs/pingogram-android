package dev.kyralabs.pingogram

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.AuthStep

/**
 * The root decision of the app (`REQUIREMENTS.md` FR-A8): whether the account is logged in. `PingogramApp` shows the
 * login flow or the main graph for it, and [MainActivity] keeps the splash screen while it is unknown.
 */
@Immutable
sealed interface MainActivityUiState {
    /** TDLib has not reported a login step yet. */
    data object Loading : MainActivityUiState

    /** Any step but [AuthStep.Ready]: logging in, logging out, or TDLib starting over after [AuthStep.Closed]. */
    data object SignedOut : MainActivityUiState

    /** [AuthStep.Ready]: the account is logged in. */
    data object SignedIn : MainActivityUiState
}

/**
 * The state for a step TDLib reported; null for [AuthStep.Initializing], which keeps the current state: unknown at
 * first, signed out while a new TDLib instance starts after logging out.
 */
internal fun AuthStep.toMainActivityUiState(): MainActivityUiState? =
    when (this) {
        AuthStep.Initializing -> null
        AuthStep.Ready -> MainActivityUiState.SignedIn
        else -> MainActivityUiState.SignedOut
    }
