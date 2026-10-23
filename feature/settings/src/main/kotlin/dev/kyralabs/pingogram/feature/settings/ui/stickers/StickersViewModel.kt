package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.core.domain.repository.StickerRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSetRepository
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings → Stickers (`REQUIREMENTS.md` FR-C5): the installed sets in the user's order (`StickerRepository`), with
 * how many sets are archived and how many trending sets are new (`StickerSetRepository`), all read again whenever
 * Telegram reports a change, here or on another device.
 *
 * A dragged order shows at once and goes to Telegram; refused, the sets return to Telegram's order with a snackbar.
 * Archiving and removing a set leave the list at once and offer to undo, which installs the set again at its place.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StickersViewModel
    @Inject
    constructor(
        private val stickerRepository: StickerRepository,
        private val stickerSetRepository: StickerSetRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<StickersUiState>(StickersUiState.Loading)
        val uiState: StateFlow<StickersUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<StickersEffect>(Channel.BUFFERED)
        val effects: Flow<StickersEffect> = effectChannel.receiveAsFlow()

        private val retries = MutableStateFlow(0)

        /** The sets being archived or removed, left out of the list until Telegram's next order. */
        private val leaving = MutableStateFlow(emptySet<Long>())

        init {
            viewModelScope.launch {
                combine(
                    retries.flatMapLatest { stickerRepository.installedSets() },
                    stickerSetRepository.archivedSets().onStart { emit(NOT_YET) },
                    stickerSetRepository.trendingSets().onStart { emit(NOT_YET) },
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
                            mutableUiState.value = outcome.value
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
            mutableUiState.value = content.copy(installed = setIds.map { byId.getValue(it) }.toImmutableList())
            viewModelScope.launch {
                val outcome = stickerSetRepository.reorder(setIds)
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
            mutableUiState.value = content.copy(installed = content.installed.filter { it.id != id }.toImmutableList())
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
                if (order != null) stickerSetRepository.reorder(order)
            }
        }

        private fun showError(error: DomainError) {
            if (error != DomainError.Silent) effectChannel.trySend(StickersEffect.ShowError(error))
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
