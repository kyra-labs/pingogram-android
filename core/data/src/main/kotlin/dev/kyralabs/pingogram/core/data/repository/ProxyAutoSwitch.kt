package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.ProxyAutoSwitchRepository
import dev.kyralabs.pingogram.core.domain.repository.ProxyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Switches to a working proxy when the one in use fails (`REQUIREMENTS.md` FR-X27), while "Switch to a working proxy"
 * is on ([ProxyAutoSwitchRepository]): once the active account has been connecting through its proxy for [STUCK]
 * (`connectionStateConnectingToProxy`), the user's other proxies are pinged, at most [MAX_PINGS] at a time, and the
 * quickest that answers is used through the app's [ProxyRepository] ([MirroredProxyRepository]), so every account
 * switches. [switched] says which. With none answering nothing changes: it never turns the proxy off by itself. While
 * it keeps connecting it tries again every [STUCK].
 */
@Singleton
class ProxyAutoSwitch
    @Inject
    constructor(
        private val graphs: AccountGraphs,
        private val settings: ProxyAutoSwitchRepository,
        private val proxies: Provider<ProxyRepository>,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val switchedProxies = MutableSharedFlow<SavedProxy>(extraBufferCapacity = 1)

        /** Each proxy switched to, for the app's snackbar. */
        val switched: Flow<SavedProxy> = switchedProxies.asSharedFlow()

        private val pings = Semaphore(MAX_PINGS)

        fun start() {
            scope.launch {
                combine(settings.enabled, activeStatus()) { on, status -> on && status == CONNECTING }
                    .distinctUntilChanged()
                    .collectLatest { stuck ->
                        while (stuck) {
                            delay(STUCK)
                            switchOnce()
                        }
                    }
            }
        }

        /** Uses the quickest of the other proxies that answers, if the account uses a proxy and one does. */
        internal suspend fun switchOnce() {
            val repository = proxies.get()
            val saved = repository.saved().orEmpty()
            val quickest = saved.firstOrNull { it.isEnabled }?.let { inUse -> repository.quickest(saved - inUse) }
            if (quickest != null && repository.enable(quickest.id) is Outcome.Success) switchedProxies.emit(quickest)
        }

        /** The proxy of [candidates] that answered a ping soonest; null when none answered. */
        private suspend fun ProxyRepository.quickest(candidates: List<SavedProxy>): SavedProxy? =
            coroutineScope {
                candidates
                    .map { candidate -> async { candidate to pings.withPermit { ping(candidate.proxy) } } }
                    .awaitAll()
                    .mapNotNull { (candidate, ping) -> (ping as? Outcome.Success)?.let { candidate to it.value } }
                    .minByOrNull { (_, time) -> time }
                    ?.first
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun activeStatus(): Flow<ConnectionStatus> =
            graphs.activeSlot.flatMapLatest { slot ->
                graphs.repositories(slot)?.connectionRepository()?.connectionStatus ?: emptyFlow()
            }

        internal companion object {
            /** How long the account may connect through its proxy before another is tried. */
            val STUCK: Duration = 15.seconds

            const val MAX_PINGS = 4

            val CONNECTING = ConnectionStatus.ConnectingToProxy
        }
    }
