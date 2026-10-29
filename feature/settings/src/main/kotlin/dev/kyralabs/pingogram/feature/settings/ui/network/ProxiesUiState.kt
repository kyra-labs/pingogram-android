package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Duration

/** What the Proxy page shows (`docs/adr/0027-proxies.md`). */
@Immutable
sealed interface ProxiesUiState {
    data object Loading : ProxiesUiState

    data class Error(
        val error: DomainError,
    ) : ProxiesUiState

    /**
     * @property proxies the proxies the user added, each with how it answered its last check.
     * @property busy a change is on its way to TDLib; the rows wait meanwhile.
     * @property deleting the proxy whose removal waits for the user's confirmation; null when none does.
     */
    data class Content(
        val proxies: ImmutableList<ProxyItem>,
        val busy: Boolean = false,
        val deleting: SavedProxy? = null,
    ) : ProxiesUiState {
        /** A proxy is in use: TDLib connects through it rather than directly. */
        val isProxyOn: Boolean get() = proxies.any { it.proxy.isEnabled }
    }
}

/** A proxy of the list with its [check]. */
@Immutable
data class ProxyItem(
    val proxy: SavedProxy,
    val check: ProxyCheck,
)

/** How a proxy answered when Pingogram last pinged it. */
@Immutable
sealed interface ProxyCheck {
    data object Checking : ProxyCheck

    /** Telegram answered through it in [ping]. */
    data class Available(
        val ping: Duration,
    ) : ProxyCheck

    /** It did not get through. */
    data object Unavailable : ProxyCheck
}

/** What the user does on the Proxy page, handled by [ProxiesViewModel.onEvent]. */
sealed interface ProxiesEvent {
    /** The "Use proxy" switch: on connects through the proxy used last, off connects directly. */
    data class SetProxyOn(
        val on: Boolean,
    ) : ProxiesEvent

    /** A proxy's row: connect through it. */
    data class Use(
        val id: Int,
    ) : ProxiesEvent

    /** A proxy's "Delete": asks first. */
    data class Delete(
        val id: Int,
    ) : ProxiesEvent

    data object ConfirmDelete : ProxiesEvent

    data object DismissDelete : ProxiesEvent

    /** Ping every proxy again. */
    data object CheckAgain : ProxiesEvent

    data object Retry : ProxiesEvent
}

/** One-off side effects of [ProxiesViewModel], handled once by its route. */
sealed interface ProxiesEffect {
    /** A change failed, shown in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : ProxiesEffect

    /** The switch was turned on without a proxy to use: the page to add one opens. */
    data object AddProxy : ProxiesEffect
}
