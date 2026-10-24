package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Proxy

/**
 * What the sheet of a proxy link shows (`docs/adr/0027-proxies.md`): the proxy, how it answers, and the step.
 *
 * @property inUse Pingogram connects through this proxy already.
 * @property connecting the proxy is on its way to TDLib.
 * @property connected TDLib took it: the sheet says so, with "Done".
 * @property error why connecting failed; shown until the next try.
 */
@Immutable
data class ProxyLinkUiState(
    val proxy: Proxy,
    val check: ProxyCheck = ProxyCheck.Checking,
    val inUse: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val error: DomainError? = null,
)

/** What the user does on the sheet, handled by [ProxyLinkViewModel.onEvent]. */
sealed interface ProxyLinkEvent {
    /** "Connect": add the proxy and connect through it. */
    data object Connect : ProxyLinkEvent

    /** Ping the proxy again. */
    data object CheckAgain : ProxyLinkEvent
}
