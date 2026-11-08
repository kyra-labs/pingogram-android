package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.core.domain.repository.StickerSetRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSettings
import dev.kyralabs.pingogram.core.domain.repository.StickerSettingsRepository
import dev.kyralabs.pingogram.core.navigation.StickersRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings → Stickers (`REQUIREMENTS.md` FR-C5), or its Emoji page ([kind]): the installed sets of the kind in the
 * user's order, with how many sets are archived and how many trending sets are new (`StickerSetRepository`), all read
 * again whenever Telegram reports a change, here or on another device.
 *
 * A dragged order shows at once and goes to Telegram; refused, the sets return to Telegram's order with a snackbar.
 * Archiving and removing a set leave the list at once and offer to undo, which installs the set again at its place.
 * The search field finds the user's sets on the device and Telegram's catalog ([StickerSearchState]). The stickers'
 * page also holds the stickers' own settings ([StickerSettingsRepository]): suggestions by emoji and looping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StickersViewModel internal constructor(
    private val kind: StickerSetKind,
    private val stickerSetRepository: StickerSetRepository,
    private val stickerSettingsRepository: StickerSettingsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        stickerSetRepository: StickerSetRepository,
        stickerSettingsRepository: StickerSettingsRepository,
    ) : this(
        stickerSetKind(savedStateHandle.toRoute<StickersRoute>().emoji),
        stickerSetRepository,
        stickerSettingsRepository,
        savedStateHandle,
    )

    private val mutableUiState = MutableStateFlow<StickersUiState>(StickersUiState.Loading)

    /** The search field's words, kept across process death. */
    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    private val search = StickerSearchState(kind, stickerSetRepository, query, viewModelScope)

    /** The stickers' own settings, on the stickers' page only: the Emoji page shows none. */
    private val settings: StateFlow<StickerSettings?> =
        (if (kind == StickerSetKind.Stickers) stickerSettingsRepository.settings else flowOf(null))
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<StickersUiState> = mutableUiState.asStateFlow()

    /** Shows [state], with what the search found and the settings, as they are now, where it is the page's content. */
    private fun show(state: StickersUiState) {
        mutableUiState.value =
            if (state is StickersUiState.Content) {
                state.copy(
                    search = search.results(state.installed, search.catalog.value, search.adding.value),
                    settings = settings.value,
                )
            } else {
                state
            }
    }

    private val effectChannel = Channel<StickersEffect>(Channel.BUFFERED)
    val effects: Flow<StickersEffect> = effectChannel.receiveAsFlow()

    private val retries = MutableStateFlow(0)

    /** The sets being archived or removed, left out of the list until Telegram's next order. */
    private val leaving = MutableStateFlow(emptySet<Long>())

    init {
        // The search's results and the settings change apart from the sets: show them as they come.
        viewModelScope.launch {
            combine(search.catalog, search.adding, settings) { _, _, _ -> }.collect {
                (mutableUiState.value as? StickersUiState.Content)?.let(::show)
            }
        }
        viewModelScope.launch {
            combine(
                retries.flatMapLatest { stickerSetRepository.installedSets(kind) },
                stickerSetRepository.archivedSets(kind).onStart { emit(NOT_YET) },
                stickerSetRepository.trendingSets(kind).onStart { emit(NOT_YET) },
                leaving,
            ) { installed, archived, trending, leavingIds ->
                installed.map { sets ->
                    // A set Telegram no longer lists has left: should it come back, it shows again.
                    val listed = sets.mapTo(HashSet()) { it.id }
                    if (leavingIds.any { it !in listed }) {
                        leaving.update { ids -> ids.filterTo(HashSet()) { it in listed } }
                    }
                    StickersUiState.Content(
                        installed = sets.filter { it.id !in leavingIds }.map { it.toItem() }.toImmutableList(),
                        archivedCount = archived.getOrNull()?.size,
                        newTrendingCount = trending.getOrNull()?.count { !it.isViewed && !it.isInstalled },
                    )
                }
            }.collectLatest { outcome ->
                when (outcome) {
                    is Outcome.Success -> {
                        show(outcome.value)
                    }

                    is Outcome.Failure -> {
                        // The list stays while it is on screen; a later read replaces it.
                        if (uiState.value !is StickersUiState.Content) {
                            mutableUiState.value = StickersUiState.Error(outcome.error)
                        }
                    }
                }
            }
        }
    }

    fun onEvent(event: StickersEvent) {
        when (event) {
            StickersEvent.Retry -> if (uiState.value is StickersUiState.Error) retry()
            is StickersEvent.Reorder -> reorder(event.setIds)
            is StickersEvent.Move -> move(event.id, event.up)
            is StickersEvent.Archive -> change(event.id, archived = true)
            is StickersEvent.Remove -> change(event.id, archived = false)
            is StickersEvent.Undo -> undo(event.id, event.previousOrder)
            is StickersEvent.Search -> savedStateHandle[KEY_QUERY] = event.query
            StickersEvent.RetrySearch -> search.retry()
            is StickersEvent.AddFound -> addFound(event.id)
            is StickersEvent.SetSuggestions -> save { stickerSettingsRepository.setSuggestions(event.suggestions) }
            is StickersEvent.SetLoop -> save { stickerSettingsRepository.setLoop(event.loop) }
        }
    }

    /** Saves a sticker setting; a refusal (a full disk) is a snackbar, and the setting stays as it was. */
    private fun save(change: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            val outcome = change()
            if (outcome is Outcome.Failure) showError(outcome.error)
        }
    }

    private fun addFound(id: Long) {
        if (!search.startAdding(id)) return
        viewModelScope.launch {
            val outcome = stickerSetRepository.install(id)
            search.doneAdding(id, installed = outcome is Outcome.Success)
            if (outcome is Outcome.Failure) showError(outcome.error)
        }
    }

    private fun retry() {
        mutableUiState.value = StickersUiState.Loading
        retries.update { it + 1 }
    }

    private fun reorder(setIds: List<Long>) {
        val content = uiState.value as? StickersUiState.Content ?: return
        val byId = content.installed.associateBy { it.id }
        if (setIds.toSet() != byId.keys || setIds == content.installed.map { it.id }) return
        show(content.copy(installed = setIds.map { byId.getValue(it) }.toImmutableList()))
        viewModelScope.launch {
            val outcome = stickerSetRepository.reorder(setIds, kind)
            if (outcome is Outcome.Failure) {
                // Telegram's order returns with the next read of the sets.
                retries.update { it + 1 }
                showError(outcome.error)
            }
        }
    }

    private fun move(
        id: Long,
        up: Boolean,
    ) {
        val ids = (uiState.value as? StickersUiState.Content)?.installed?.map { it.id } ?: return
        val index = ids.indexOf(id)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in ids.indices) return
        reorder(ids.toMutableList().apply { add(target, removeAt(index)) })
    }

    private fun change(
        id: Long,
        archived: Boolean,
    ) {
        val content = uiState.value as? StickersUiState.Content ?: return
        val set = content.installed.firstOrNull { it.id == id } ?: return
        val previousOrder = content.installed.map { it.id }
        leaving.update { it + id }
        show(content.copy(installed = content.installed.filter { it.id != id }.toImmutableList()))
        viewModelScope.launch {
            val outcome =
                if (archived) stickerSetRepository.archive(id) else stickerSetRepository.remove(id)
            when (outcome) {
                // It stays out of the list: Telegram's next order no longer holds it.
                is Outcome.Success -> {
                    effectChannel.send(StickersEffect.Changed(id, set.title, archived, previousOrder))
                }

                is Outcome.Failure -> {
                    leaving.update { it - id }
                    showError(outcome.error)
                }
            }
        }
    }

    /** Installs set [id] again, which puts it first, then back where it was among the sets of [previousOrder]. */
    private fun undo(
        id: Long,
        previousOrder: List<Long>,
    ) {
        leaving.update { it - id }
        viewModelScope.launch {
            val installed = stickerSetRepository.install(id)
            if (installed is Outcome.Failure) {
                showError(installed.error)
                return@launch
            }
            val current = (uiState.value as? StickersUiState.Content)?.installed?.map { it.id }.orEmpty()
            val order = restoredOrder(id, previousOrder, current)
            if (order != null) stickerSetRepository.reorder(order, kind)
        }
    }

    private fun showError(error: DomainError) {
        if (error != DomainError.Silent) effectChannel.trySend(StickersEffect.ShowError(error))
    }

    private companion object {
        const val KEY_QUERY = "stickers.query"
    }
}

/**
 * The installed sets' order once set [id] is back where it was in [previousOrder]: the sets now installed
 * ([current], which may hold [id] already and sets installed since), [id] after the set it followed; null when it is
 * already there.
 */
internal fun restoredOrder(
    id: Long,
    previousOrder: List<Long>,
    current: List<Long>,
): List<Long>? {
    val others = current.filter { it != id }
    val before = previousOrder.takeWhile { it != id }.lastOrNull { it in others }
    val order = others.toMutableList().apply { add(if (before == null) 0 else indexOf(before) + 1, id) }
    return order.takeIf { it != current }
}

/** A list not read yet: the page shows without its count meanwhile. */
private val NOT_YET: Outcome<List<StickerSetSummary>> = Outcome.Failure(DomainError.Silent)
