package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProxyKind

/** What the page that adds or edits a proxy shows (`docs/adr/0027-proxies.md`). */
@Immutable
sealed interface EditProxyUiState {
    /** The proxy being edited is read from TDLib. */
    data object Loading : EditProxyUiState

    /** The proxy being edited could not be read, or is gone. */
    data class Error(
        val error: DomainError,
    ) : EditProxyUiState

    /**
     * The form. Passwords and secrets are in [ProxyForm] but never in the saved state (`docs/ARCHITECTURE.md`).
     *
     * @property isNew it adds a proxy, which Pingogram then connects through; else it edits one.
     * @property errors the fields that cannot be sent as they are, shown after "Save" or "Check".
     * @property check how the form's proxy answered "Check"; null before it was checked or after it changed.
     * @property saving the proxy is on its way to TDLib.
     */
    data class Form(
        val form: ProxyForm,
        val isNew: Boolean,
        val errors: Set<ProxyField> = emptySet(),
        val check: ProxyCheck? = null,
        val saving: Boolean = false,
    ) : EditProxyUiState
}

/** What the user typed for a proxy; the fields of the other protocols stay while the protocol changes. */
@Immutable
data class ProxyForm(
    val kind: ProxyKind = ProxyKind.Socks5,
    val server: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
    val secret: String = "",
    val httpOnly: Boolean = false,
    val name: String = "",
)

/** The fields of a [ProxyForm] that can be wrong. */
enum class ProxyField { Server, Port, Secret }

/** What the user does on the page, handled by [EditProxyViewModel.onEvent]. */
sealed interface EditProxyEvent {
    data class Change(
        val form: ProxyForm,
    ) : EditProxyEvent

    /** Ping the proxy as typed. */
    data object Check : EditProxyEvent

    data object Save : EditProxyEvent

    data object Retry : EditProxyEvent
}

/** One-off side effects of [EditProxyViewModel], handled once by its route. */
sealed interface EditProxyEffect {
    /** Saved: the page closes. */
    data object Saved : EditProxyEffect

    data class ShowError(
        val error: DomainError,
    ) : EditProxyEffect
}
