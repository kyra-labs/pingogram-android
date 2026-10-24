package dev.kyralabs.pingogram.feature.settings.ui.websites

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * What the Websites page shows: the [websites], the log-out being confirmed ([dialog]), the websites being logged out
 * ([busyIds]), and whether all are ([isDisconnectingAll]).
 */
@Immutable
data class ConnectedWebsitesUiState(
    val websites: WebsitesState = WebsitesState.Loading,
    val dialog: DisconnectDialog? = null,
    val busyIds: ImmutableSet<Long> = persistentSetOf(),
    val isDisconnectingAll: Boolean = false,
)

@Immutable
sealed interface WebsitesState {
    data object Loading : WebsitesState

    data class Failed(
        val error: DomainError,
    ) : WebsitesState

    data class Loaded(
        val websites: ImmutableList<WebsiteItem>,
    ) : WebsitesState
}

/**
 * A website as its row shows it, its texts made by the view model: the [domain], the bot that logged the user in
 * with the browser and system ([details]: "Example Bot · Firefox, Linux"), and where and when it was last active
 * ([activity]: "203.0.113.1 · Berlin, Germany · 9:41 AM").
 */
@Immutable
data class WebsiteItem(
    val id: Long,
    val domain: String,
    val details: UiText,
    val activity: UiText,
)

/** A log-out waiting for confirmation (`AlertDialog`, destructive). */
@Immutable
sealed interface DisconnectDialog {
    data class Website(
        val id: Long,
        val domain: String,
    ) : DisconnectDialog

    data object All : DisconnectDialog
}

sealed interface ConnectedWebsitesEvent {
    /** The page shows (again): load the websites again, keeping what shows meanwhile. */
    data object Refresh : ConnectedWebsitesEvent

    data object Retry : ConnectedWebsitesEvent

    /** A website's "Log out": ask to confirm. */
    data class Disconnect(
        val website: WebsiteItem,
    ) : ConnectedWebsitesEvent

    /** "Log out of all websites": ask to confirm. */
    data object DisconnectAll : ConnectedWebsitesEvent

    data object Confirm : ConnectedWebsitesEvent

    data object Dismiss : ConnectedWebsitesEvent
}

sealed interface ConnectedWebsitesEffect {
    /** Website [domain] was logged out. */
    data class Disconnected(
        val domain: String,
    ) : ConnectedWebsitesEffect

    data object AllDisconnected : ConnectedWebsitesEffect

    data class ShowError(
        val error: DomainError,
    ) : ConnectedWebsitesEffect
}
