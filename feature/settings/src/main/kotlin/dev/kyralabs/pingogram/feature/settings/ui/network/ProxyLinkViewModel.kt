package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.ProxyKind
import dev.kyralabs.pingogram.core.domain.model.ProxyType
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import dev.kyralabs.pingogram.core.navigation.ProxyLinkRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The sheet of a proxy link (`tg://proxy`, `t.me/socks`, `docs/adr/0027-proxies.md`): shows the proxy, pings it, and
 * adds it and connects through it only on "Connect"; a proxy the user has already is used again rather than added
 * twice. Nothing is changed before that tap: a link may come from anyone.
 */
@HiltViewModel
class ProxyLinkViewModel internal constructor(
    private val repository: ProxyRepository,
    proxy: Proxy,
) : ViewModel() {
    @Inject
    constructor(
        repository: ProxyRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(repository, savedStateHandle.toRoute<ProxyLinkRoute>().toProxy())

    private val mutableUiState = MutableStateFlow(ProxyLinkUiState(proxy))
    val uiState: StateFlow<ProxyLinkUiState> = mutableUiState.asStateFlow()

    private var pingJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = (repository.proxies.first() as? Outcome.Success)?.value.orEmpty()
            mutableUiState.update { state -> state.copy(inUse = saved.any { it.proxy == proxy && it.isEnabled }) }
        }
        ping()
    }

    fun onEvent(event: ProxyLinkEvent) {
        when (event) {
            ProxyLinkEvent.Connect -> connect()
            ProxyLinkEvent.CheckAgain -> if (uiState.value.check != ProxyCheck.Checking) ping()
        }
    }

    private fun ping() {
        pingJob?.cancel()
        val proxy = uiState.value.proxy
        mutableUiState.update { it.copy(check = ProxyCheck.Checking) }
        pingJob =
            viewModelScope.launch {
                val check =
                    when (val outcome = repository.ping(proxy)) {
                        is Outcome.Success -> ProxyCheck.Available(outcome.value)
                        is Outcome.Failure -> ProxyCheck.Unavailable
                    }
                mutableUiState.update { it.copy(check = check) }
            }
    }

    private fun connect() {
        val state = uiState.value
        if (state.connecting || state.connected) return
        mutableUiState.value = state.copy(connecting = true, error = null)
        viewModelScope.launch {
            val saved = (repository.proxies.first() as? Outcome.Success)?.value.orEmpty()
            val existing = saved.firstOrNull { it.proxy == state.proxy }
            val outcome =
                if (existing != null) {
                    repository.enable(existing.id)
                } else {
                    when (val added = repository.add(state.proxy, name = "", enable = true)) {
                        is Outcome.Success -> Outcome.Success(Unit)
                        is Outcome.Failure -> added
                    }
                }
            mutableUiState.update {
                when (outcome) {
                    is Outcome.Success -> it.copy(connecting = false, connected = true, inUse = true)
                    is Outcome.Failure -> it.copy(connecting = false, error = outcome.error)
                }
            }
        }
    }
}

/** The proxy a link's route carries. */
internal fun ProxyLinkRoute.toProxy(): Proxy =
    Proxy(
        server = server,
        port = port,
        type =
            when (ProxyKind.entries.firstOrNull { it.name == kind }) {
                ProxyKind.Http -> ProxyType.Http(username, password, httpOnly)
                ProxyKind.Mtproto -> ProxyType.Mtproto(secret)
                ProxyKind.Socks5, null -> ProxyType.Socks5(username, password)
            },
    )
