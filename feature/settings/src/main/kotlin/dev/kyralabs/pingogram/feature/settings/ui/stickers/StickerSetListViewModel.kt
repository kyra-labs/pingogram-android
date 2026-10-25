package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.core.domain.repository.StickerSetRepository
import dev.kyralabs.pingogram.core.navigation.StickerSetListRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings → Stickers' Trending stickers ([archived] false) or Archived stickers page (`REQUIREMENTS.md` FR-C5), or
 * Trending emoji and Archived emoji for custom emoji sets ([kind]): the sets of `StickerSetRepository.trendingSets` or
 * `archivedSets`, read again whenever Telegram reports a change, each with "Add", which installs it. The trending sets
 * shown are marked as seen once, as Telegram's apps do, so they no longer count as new.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StickerSetListViewModel internal constructor(
    private val archived: Boolean,
    private val repository: StickerSetRepository,
    private val kind: StickerSetKind = StickerSetKind.Stickers,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: StickerSetRepository,
    ) : this(
        savedStateHandle.toRoute<StickerSetListRoute>().archived,
        repository,
        stickerSetKind(savedStateHandle.toRoute<StickerSetListRoute>().emoji),
    )

    private val mutableUiState = MutableStateFlow<StickerSetListUiState>(StickerSetListUiState.Loading)
    val uiState: StateFlow<StickerSetListUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<StickerSetListEffect>(Channel.BUFFERED)
    val effects: Flow<StickerSetListEffect> = effectChannel.receiveAsFlow()

    private val retries = MutableStateFlow(0)

    /** The sets being added; a set stays busy until Telegram reports it installed. */
    private val adding = MutableStateFlow(emptySet<Long>())

    private var markedViewed = false

    init {
        viewModelScope.launch {
            val sets =
                retries.flatMapLatest { if (archived) repository.archivedSets(kind) else repository.trendingSets(kind) }
            combine(sets, adding) { outcome, busy -> outcome to busy }.collectLatest { (outcome, busy) ->
                when (outcome) {
                    is Outcome.Success -> {
                        mutableUiState.value =
                            StickerSetListUiState.Content(
                                outcome.value
                                    .map { it.toItem(isBusy = it.id in busy && !it.isInstalled) }
                                    .toImmutableList(),
                            )
                        if (!archived) markViewed(outcome.value.filter { !it.isViewed }.map { it.id })
                    }

                    is Outcome.Failure -> {
                        if (uiState.value !is StickerSetListUiState.Content) {
                            mutableUiState.value = StickerSetListUiState.Error(outcome.error)
                        }
                    }
                }
            }
        }
    }

    fun onEvent(event: StickerSetListEvent) {
        when (event) {
            StickerSetListEvent.Retry -> {
                if (uiState.value is StickerSetListUiState.Error) {
                    mutableUiState.value = StickerSetListUiState.Loading
                    retries.update { it + 1 }
                }
            }

            is StickerSetListEvent.Add -> {
                add(event.id)
            }
        }
    }

    private fun add(id: Long) {
        val set = (uiState.value as? StickerSetListUiState.Content)?.sets?.firstOrNull { it.id == id } ?: return
        if (set.isInstalled || id in adding.value) return
        adding.update { it + id }
        viewModelScope.launch {
            when (val outcome = repository.install(id)) {
                is Outcome.Success -> {
                    effectChannel.send(StickerSetListEffect.Added(set.title))
                }

                is Outcome.Failure -> {
                    adding.update { it - id }
                    if (outcome.error != DomainError.Silent) {
                        effectChannel.send(StickerSetListEffect.ShowError(outcome.error))
                    }
                }
            }
        }
    }

    /** Marks the trending sets the page showed first as seen, once: failing is no reason to tell the user. */
    private fun markViewed(ids: List<Long>) {
        if (markedViewed || ids.isEmpty()) return
        markedViewed = true
        viewModelScope.launch { repository.markTrendingViewed(ids) }
    }
}
