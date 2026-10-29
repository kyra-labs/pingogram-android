package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.SessionDevice
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * What the Active sessions page shows (`REQUIREMENTS.md` FR-S1).
 *
 * @property sessions the sessions: loading, loaded, or why they could not be loaded.
 * @property dialog the termination being confirmed; null when none is.
 * @property busySessionIds the sessions being terminated or confirmed.
 * @property isTerminatingAll terminating all other sessions was confirmed and runs.
 * @property isOnTestServers this session signed in on Telegram's test servers (`REQUIREMENTS.md` FR-A9): this device's
 *   row says so.
 */
@Immutable
data class ActiveSessionsUiState(
    val sessions: SessionsState = SessionsState.Loading,
    val dialog: TerminateDialog? = null,
    val busySessionIds: ImmutableSet<Long> = persistentSetOf(),
    val isTerminatingAll: Boolean = false,
    val isOnTestServers: Boolean = false,
)

/** The sessions while they load. */
@Immutable
sealed interface SessionsState {
    data object Loading : SessionsState

    data class Failed(
        val error: DomainError,
    ) : SessionsState

    /**
     * The sessions, in TDLib's order within each group.
     *
     * @property current this app on this device; null if TDLib did not mark one as current.
     * @property unconfirmed new logins no other session has confirmed yet.
     * @property others every other session.
     * @property inactiveSessionTtlDays after how many days without activity Telegram terminates a session.
     */
    data class Loaded(
        val current: SessionItem?,
        val unconfirmed: ImmutableList<SessionItem>,
        val others: ImmutableList<SessionItem>,
        val inactiveSessionTtlDays: Int,
    ) : SessionsState
}

/**
 * A session as its row shows it, its texts formatted by the view model.
 *
 * @property title the device model ("Example Phone 8"), or "Unknown device".
 * @property app the app and the system: "Pingogram 0.3.0 · Android 15".
 * @property activity where and when: "Berlin, Germany · 9:41 AM", "Online" for this device.
 * @property isPasswordPending the 2-step verification password was not entered yet.
 */
@Immutable
data class SessionItem(
    val id: Long,
    val device: SessionDevice,
    val title: UiText,
    val app: UiText,
    val activity: UiText,
    val isPasswordPending: Boolean = false,
)

/** A termination waiting for confirmation (`AlertDialog`, destructive). */
@Immutable
sealed interface TerminateDialog {
    /** Terminate session [sessionId], described as [title]. */
    data class Session(
        val sessionId: Long,
        val title: UiText,
    ) : TerminateDialog

    data object AllOthers : TerminateDialog
}

/** What the user does on the Active sessions page, handled by [ActiveSessionsViewModel.onEvent]. */
sealed interface ActiveSessionsEvent {
    /** The page shows (again): load the sessions again, keeping what shows meanwhile. */
    data object Refresh : ActiveSessionsEvent

    /** Load the sessions again after they failed. */
    data object Retry : ActiveSessionsEvent

    /** A session was tapped, or a new login was not the user's: ask to confirm its termination. */
    data class Terminate(
        val session: SessionItem,
    ) : ActiveSessionsEvent

    /** "Terminate all other sessions" was tapped: ask for confirmation. */
    data object TerminateAllOthers : ActiveSessionsEvent

    /** The open confirmation was accepted. */
    data object ConfirmTermination : ActiveSessionsEvent

    data object DismissTermination : ActiveSessionsEvent

    /** A new login is the user's own. */
    data class ConfirmLogin(
        val sessionId: Long,
    ) : ActiveSessionsEvent
}

/** One-off side effects of [ActiveSessionsViewModel], shown once in snackbars by its route. */
sealed interface ActiveSessionsEffect {
    data object SessionTerminated : ActiveSessionsEffect

    data object OtherSessionsTerminated : ActiveSessionsEffect

    data object LoginConfirmed : ActiveSessionsEffect

    /**
     * Telegram refused a termination with error 406, whose text must not be shown; most likely this device logged in
     * too recently to terminate other sessions.
     */
    data object TerminationRefused : ActiveSessionsEffect

    data class ShowError(
        val error: DomainError,
    ) : ActiveSessionsEffect
}
