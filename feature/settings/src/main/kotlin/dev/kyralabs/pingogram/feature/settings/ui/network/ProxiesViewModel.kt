package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * The Proxy page (`docs/adr/0027-proxies.md`): the proxies TDLib keeps, which one it connects through, and how each
 * answers. Every proxy is pinged once when it first shows, at most [MAX_PINGS] at a time, and again on
 * [ProxiesEvent.CheckAgain]; a ping goes to that proxy only. The list follows [ProxyRepository.proxies], so a proxy
 * added, edited or connected from a link shows at once.
 */
@HiltViewModel
class ProxiesViewModel
    @Inject
    constructor(
        private val repository: ProxyRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<ProxiesUiState>(ProxiesUiState.Loading)
        val uiState: StateFlow<ProxiesUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<ProxiesEffect>(Channel.BUFFERED)
        val effects: Flow<ProxiesEffect> = effectChannel.receiveAsFlow()

        /** How every proxy answered, by what it is, so an edited proxy is pinged anew and a moved one is not. */
        private val checks = MutableStateFlow<Map<Proxy, ProxyCheck>>(emptyMap())
        private val pings = Semaphore(MAX_PINGS)
        private var saved: ImmutableList<SavedProxy> = persistentListOf()
        private var loadJob: Job? = null

        init {
            load()
        }

        fun onEvent(event: ProxiesEvent) {
            when (event) {
                is ProxiesEvent.SetProxyOn -> {
                    setProxyOn(event.on)
                }

                is ProxiesEvent.Use -> {
                    if (saved.none { it.id == event.id && it.isEnabled }) change { repository.enable(event.id) }
                }

                is ProxiesEvent.Delete -> {
                    askToDelete(event.id)
                }

                ProxiesEvent.ConfirmDelete -> {
                    confirmDelete()
                }

                ProxiesEvent.DismissDelete -> {
                    updateContent { it.copy(deleting = null) }
                }

                ProxiesEvent.CheckAgain -> {
                    checkAgain()
                }

                ProxiesEvent.Retry -> {
                    if (uiState.value is ProxiesUiState.Error) load()
                }
            }
        }

        private fun load() {
            loadJob?.cancel()
            mutableUiState.value = ProxiesUiState.Loading
            loadJob =
                viewModelScope.launch {
                    repository.proxies.collect { outcome ->
                        when (outcome) {
                            is Outcome.Success -> {
                                saved = outcome.value
                                saved.map { it.proxy }.filter { it !in checks.value }.forEach(::ping)
                                show()
                            }

                            is Outcome.Failure -> {
                                if (uiState.value is ProxiesUiState.Content) {
                                    effectChannel.trySend(ProxiesEffect.ShowError(outcome.error))
                                } else {
                                    mutableUiState.value = ProxiesUiState.Error(outcome.error)
                                }
                            }
                        }
                    }
                }
        }

        private fun show() {
            val items = saved.map { ProxyItem(it, checks.value[it.proxy] ?: ProxyCheck.Checking) }.toImmutableList()
            mutableUiState.update { state ->
                when (state) {
                    is ProxiesUiState.Content -> {
                        state.copy(
                            proxies = items,
                            deleting = state.deleting?.takeIf { deleting -> saved.any { it.id == deleting.id } },
                        )
                    }

                    else -> {
                        ProxiesUiState.Content(items)
                    }
                }
            }
        }

        private fun ping(proxy: Proxy) {
            checks.update { it + (proxy to ProxyCheck.Checking) }
            viewModelScope.launch {
                val check =
                    when (val outcome = pings.withPermit { repository.ping(proxy) }) {
                        is Outcome.Success -> ProxyCheck.Available(outcome.value)
                        is Outcome.Failure -> ProxyCheck.Unavailable
                    }
                checks.update { it + (proxy to check) }
                show()
            }
        }

        private fun checkAgain() {
            if (uiState.value !is ProxiesUiState.Content) return
            saved
                .map { it.proxy }
                .distinct()
                .filter { checks.value[it] != ProxyCheck.Checking }
                .forEach(::ping)
            show()
        }

        private fun setProxyOn(on: Boolean) {
            val content = uiState.value as? ProxiesUiState.Content ?: return
            // The one used last, as Telegram's apps turn proxies back on; else the first one added.
            val last = saved.maxByOrNull { it.lastUsedDate }?.takeIf { it.lastUsedDate > 0 } ?: saved.firstOrNull()
            when {
                on == content.isProxyOn -> Unit
                !on -> change { repository.disable() }
                last == null -> effectChannel.trySend(ProxiesEffect.AddProxy)
                else -> change { repository.enable(last.id) }
            }
        }

        private fun askToDelete(id: Int) {
            val proxy = saved.firstOrNull { it.id == id } ?: return
            updateContent { it.copy(deleting = proxy) }
        }

        private fun confirmDelete() {
            val content = uiState.value as? ProxiesUiState.Content ?: return
            val proxy = content.deleting ?: return
            mutableUiState.value = content.copy(deleting = null)
            change { repository.remove(proxy.id) }
        }

        /** Sends [request], the rows waiting meanwhile; the list follows by itself once it is done. */
        private fun change(request: suspend () -> Outcome<Unit>) {
            val content = uiState.value as? ProxiesUiState.Content ?: return
            if (content.busy) return
            mutableUiState.value = content.copy(busy = true)
            viewModelScope.launch {
                val outcome = request()
                updateContent { it.copy(busy = false) }
                val error = (outcome as? Outcome.Failure)?.error
                if (error != null && error != DomainError.Silent) effectChannel.trySend(ProxiesEffect.ShowError(error))
            }
        }

        private fun updateContent(transform: (ProxiesUiState.Content) -> ProxiesUiState.Content) =
            mutableUiState.update { if (it is ProxiesUiState.Content) transform(it) else it }

        private companion object {
            const val MAX_PINGS = 4
        }
    }
