package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.ProxyKind
import dev.kyralabs.pingogram.core.domain.model.ProxyType
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.feature.settings.ui.network.EditProxyUiState
import dev.kyralabs.pingogram.feature.settings.ui.network.ProxiesUiState
import dev.kyralabs.pingogram.feature.settings.ui.network.ProxyCheck
import dev.kyralabs.pingogram.feature.settings.ui.network.ProxyField
import dev.kyralabs.pingogram.feature.settings.ui.network.ProxyForm
import dev.kyralabs.pingogram.feature.settings.ui.network.ProxyItem
import dev.kyralabs.pingogram.feature.settings.ui.network.ProxyLinkUiState
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Duration.Companion.milliseconds

/** The states of the proxy pages and the proxy link sheet, shared by previews and screenshot tests. */
internal object ProxyPreviewStates {
    private val home = SavedProxy(1, Proxy("proxy.example.org", 1080, ProxyType.Socks5()), true, 1_700_000_000, "Home")
    private val office = SavedProxy(2, Proxy("10.0.0.8", 8080, ProxyType.Http()), false, 0, "")
    private val mtproto = SavedProxy(3, Proxy("mt.example.net", 443, ProxyType.Mtproto("dd00")), false, 0, "")

    val proxies =
        ProxiesUiState.Content(
            persistentListOf(
                ProxyItem(home, ProxyCheck.Available(84.milliseconds)),
                ProxyItem(office, ProxyCheck.Unavailable),
                ProxyItem(mtproto, ProxyCheck.Checking),
            ),
        )

    val proxiesOff =
        ProxiesUiState.Content(
            persistentListOf(
                ProxyItem(home.copy(isEnabled = false), ProxyCheck.Available(84.milliseconds)),
                ProxyItem(office, ProxyCheck.Available(312.milliseconds)),
            ),
        )

    val proxiesEmpty = ProxiesUiState.Content(persistentListOf())

    val proxiesDeleting = proxies.copy(deleting = home)

    val proxiesLoading = ProxiesUiState.Loading

    val proxiesError = ProxiesUiState.Error(DomainError.Network)

    val newProxy = EditProxyUiState.Form(ProxyForm(), isNew = true)

    val editProxy =
        EditProxyUiState.Form(
            ProxyForm(ProxyKind.Socks5, "proxy.example.org", "1080", "ada", "secret", name = "Home"),
            isNew = false,
            check = ProxyCheck.Available(84.milliseconds),
        )

    val httpProxy =
        EditProxyUiState.Form(
            ProxyForm(ProxyKind.Http, "10.0.0.8", "8080", httpOnly = true),
            isNew = true,
            check = ProxyCheck.Unavailable,
        )

    val mtprotoErrors =
        EditProxyUiState.Form(
            ProxyForm(ProxyKind.Mtproto, "mt example", "70000"),
            isNew = true,
            errors = setOf(ProxyField.Server, ProxyField.Port, ProxyField.Secret),
        )

    val savingProxy = editProxy.copy(saving = true, check = null)

    val editProxyLoading = EditProxyUiState.Loading

    val editProxyError = EditProxyUiState.Error(DomainError.NotFound)

    private val linkProxy = Proxy("mt.example.net", 443, ProxyType.Mtproto("dd00"))

    val link = ProxyLinkUiState(linkProxy, check = ProxyCheck.Available(96.milliseconds))

    val linkChecking = ProxyLinkUiState(linkProxy)

    val linkInUse = link.copy(inUse = true)

    val linkConnecting = link.copy(connecting = true)

    val linkError = link.copy(check = ProxyCheck.Unavailable, error = DomainError.InputRejected)

    val linkConnected = link.copy(connected = true, inUse = true)
}
