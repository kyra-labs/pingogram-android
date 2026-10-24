package dev.kyralabs.pingogram.feature.settings.ui.websites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ConnectedWebsite
import dev.kyralabs.pingogram.core.domain.repository.ConnectedWebsiteRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsTimeFormats
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Privacy and security → Websites (`docs/audit/feature-gaps-octogram.md`, P2): the websites logged in with the account
 * ("Log in with Telegram"), loaded when the page opens and again whenever it shows, and logging one or all of them out
 * after a destructive confirmation, which survives process death. A logged-out website leaves the list at once.
 */
@HiltViewModel
class ConnectedWebsitesViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: ConnectedWebsiteRepository,
        private val userRepository: UserRepository,
        private val timeFormats: SettingsTimeFormats,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow(ConnectedWebsitesUiState(dialog = savedStateHandle.restoredDialog()))
        val uiState: StateFlow<ConnectedWebsitesUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<ConnectedWebsitesEffect>(Channel.BUFFERED)
        val effects: Flow<ConnectedWebsitesEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        init {
            load(keepContent = false)
        }

        fun onEvent(event: ConnectedWebsitesEvent) {
            when (event) {
                ConnectedWebsitesEvent.Refresh -> {
                    if (loadJob?.isActive != true) load(keepContent = true)
                }

                ConnectedWebsitesEvent.Retry -> {
                    if (uiState.value.websites is WebsitesState.Failed) load(keepContent = false)
                }

                is ConnectedWebsitesEvent.Disconnect -> {
                    if (event.website.id !in uiState.value.busyIds) {
                        setDialog(DisconnectDialog.Website(event.website.id, event.website.domain))
                    }
                }

                ConnectedWebsitesEvent.DisconnectAll -> {
                    if (!uiState.value.isDisconnectingAll) setDialog(DisconnectDialog.All)
                }

                ConnectedWebsitesEvent.Confirm -> {
                    confirm()
                }

                ConnectedWebsitesEvent.Dismiss -> {
                    setDialog(null)
                }
            }
        }

        /** Loads the websites; with [keepContent], what shows stays meanwhile, and if loading fails. */
        private fun load(keepContent: Boolean) {
            loadJob?.cancel()
            if (!keepContent || uiState.value.websites !is WebsitesState.Loaded) {
                mutableUiState.update { it.copy(websites = WebsitesState.Loading) }
            }
            loadJob =
                viewModelScope.launch {
                    when (val websites = repository.websites()) {
                        is Outcome.Success -> {
                            val items = websites.value.map { it.toItem() }.toImmutableList()
                            mutableUiState.update { it.copy(websites = WebsitesState.Loaded(items)) }
                        }

                        is Outcome.Failure -> {
                            mutableUiState.update {
                                if (it.websites is WebsitesState.Loaded) {
                                    it
                                } else {
                                    it.copy(websites = WebsitesState.Failed(websites.error))
                                }
                            }
                        }
                    }
                }
        }

        private fun confirm() {
            when (val dialog = uiState.value.dialog) {
                is DisconnectDialog.Website -> disconnect(dialog)
                DisconnectDialog.All -> disconnectAll()
                null -> Unit
            }
            setDialog(null)
        }

        private fun disconnect(dialog: DisconnectDialog.Website) {
            if (dialog.id in uiState.value.busyIds) return
            setBusy(dialog.id, busy = true)
            viewModelScope.launch {
                val disconnected = repository.disconnect(dialog.id)
                setBusy(dialog.id, busy = false)
                when (disconnected) {
                    is Outcome.Success -> {
                        updateLoaded { websites -> websites.filterNot { it.id == dialog.id } }
                        effectChannel.trySend(ConnectedWebsitesEffect.Disconnected(dialog.domain))
                    }

                    is Outcome.Failure -> {
                        showError(disconnected.error)
                    }
                }
            }
        }

        private fun disconnectAll() {
            if (uiState.value.isDisconnectingAll) return
            mutableUiState.update { it.copy(isDisconnectingAll = true) }
            viewModelScope.launch {
                val disconnected = repository.disconnectAll()
                mutableUiState.update { it.copy(isDisconnectingAll = false) }
                when (disconnected) {
                    is Outcome.Success -> {
                        updateLoaded { emptyList() }
                        effectChannel.trySend(ConnectedWebsitesEffect.AllDisconnected)
                    }

                    is Outcome.Failure -> {
                        showError(disconnected.error)
                    }
                }
            }
        }

        private fun showError(error: DomainError) {
            if (error != DomainError.Silent) effectChannel.trySend(ConnectedWebsitesEffect.ShowError(error))
        }

        private suspend fun ConnectedWebsite.toItem(): WebsiteItem {
            // The bot may not have been announced yet: getUser asks TDLib for it then.
            val bot =
                (userRepository.getUser(botUserId) as? Outcome.Success)
                    ?.value
                    ?.takeUnless { it.isDeleted }
                    ?.fullName()
            val device = listOf(browser, platform).filter { it.isNotBlank() }.joinToString(", ")
            val formatter = timeFormats.formatter()
            return WebsiteItem(
                id = id,
                domain = domain,
                details = parts(listOfNotNull(bot, plain(device))),
                activity =
                    parts(
                        listOfNotNull(
                            plain(ipAddress),
                            plain(location),
                            plain(formatter.lastActive(lastActiveAt, timeFormats.now())),
                        ),
                    ),
            )
        }

        private fun setBusy(
            id: Long,
            busy: Boolean,
        ) = mutableUiState.update {
            val ids = if (busy) it.busyIds + id else it.busyIds - id
            it.copy(busyIds = ids.toPersistentSet())
        }

        private fun updateLoaded(transform: (List<WebsiteItem>) -> List<WebsiteItem>) =
            mutableUiState.update { state ->
                val loaded = state.websites as? WebsitesState.Loaded
                if (loaded == null) {
                    state
                } else {
                    state.copy(websites = WebsitesState.Loaded(transform(loaded.websites).toImmutableList()))
                }
            }

        private fun setDialog(dialog: DisconnectDialog?) {
            mutableUiState.update { it.copy(dialog = dialog) }
            savedStateHandle[KEY_DIALOG] =
                when (dialog) {
                    is DisconnectDialog.Website -> DIALOG_WEBSITE
                    DisconnectDialog.All -> DIALOG_ALL
                    null -> null
                }
            savedStateHandle[KEY_WEBSITE_ID] = (dialog as? DisconnectDialog.Website)?.id
            savedStateHandle[KEY_DOMAIN] = (dialog as? DisconnectDialog.Website)?.domain
        }
    }

/** The confirmation the view model had open before the process died; null if none was. */
private fun SavedStateHandle.restoredDialog(): DisconnectDialog? =
    when (get<String>(KEY_DIALOG)) {
        DIALOG_ALL -> {
            DisconnectDialog.All
        }

        DIALOG_WEBSITE -> {
            get<Long>(KEY_WEBSITE_ID)?.let { DisconnectDialog.Website(it, get<String>(KEY_DOMAIN).orEmpty()) }
        }

        else -> {
            null
        }
    }

private fun plain(text: String): UiText? = text.trim().takeIf { it.isNotEmpty() }?.let { UiText.Plain(it) }

/** The [parts] joined with " · ", each isolated (`bidiIsolated`); one alone as it is; none, nothing. */
private fun parts(parts: List<UiText>): UiText =
    parts.drop(1).fold(parts.firstOrNull() ?: UiText.Plain("")) { joined, part ->
        UiText.resource(R.string.settings_website_parts, joined.bidiIsolated(), part.bidiIsolated())
    }

private const val KEY_DIALOG = "websites.dialog"
private const val KEY_WEBSITE_ID = "websites.dialogWebsiteId"
private const val KEY_DOMAIN = "websites.dialogDomain"
private const val DIALOG_WEBSITE = "website"
private const val DIALOG_ALL = "all"
