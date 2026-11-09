package dev.kyralabs.pingogram.feature.settings.ui.stickers

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.core.domain.repository.StickerSetRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * The search of Settings → Stickers (or its Emoji page), a part of [StickersViewModel]: Telegram's catalog for the
 * words of [query] (`StickerSetRepository.searchSets`), asked [SEARCH_DELAY_MILLIS] after the last change, the newest
 * words cancelling the search still running, and the user's own sets matched on the device ([results]). "Add" installs
 * a found set ([startAdding], [doneAdding]); it shows as added until the search runs again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StickerSearchState(
    private val kind: StickerSetKind,
    private val repository: StickerSetRepository,
    private val query: StateFlow<String>,
    scope: CoroutineScope,
) {
    private val retries = MutableStateFlow(0)
    private val mutableAdding = MutableStateFlow(Adding())

    /** The sets being added, and those added since the search ran. */
    val adding: StateFlow<Adding> = mutableAdding.asStateFlow()

    /** Telegram's sets for the words, or null while the field is empty. */
    val catalog: StateFlow<StickerCatalog?> =
        combine(query, retries) { words, _ -> words.trim() }
            .flatMapLatest { words -> if (words.isEmpty()) flowOf(null) else searched(words) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private fun searched(words: String): Flow<StickerCatalog> =
        flow {
            emit(StickerCatalog.Searching)
            delay(SEARCH_DELAY_MILLIS)
            mutableAdding.update { it.copy(added = emptySet()) }
            when (val outcome = repository.searchSets(words, kind)) {
                is Outcome.Success -> emit(StickerCatalog.Found(outcome.value.map { it.toItem() }.toImmutableList()))
                is Outcome.Failure -> emit(StickerCatalog.Failed(outcome.error))
            }
        }

    /** Searches Telegram again, after a failure. */
    fun retry() {
        retries.update { it + 1 }
    }

    /** Marks set [id] as being added; false when it already is. */
    fun startAdding(id: Long): Boolean {
        if (id in adding.value.busy) return false
        mutableAdding.update { it.copy(busy = it.busy + id) }
        return true
    }

    /** Set [id] was added ([installed]) or Telegram refused it. */
    fun doneAdding(
        id: Long,
        installed: Boolean,
    ) {
        mutableAdding.update {
            it.copy(busy = it.busy - id, added = if (installed) it.added + id else it.added)
        }
    }

    /**
     * The results for [catalog]: the user's sets among [installed] whose title or name has the words, then
     * Telegram's others, marked as being added or added ([adding]); null while the field is empty.
     */
    fun results(
        installed: ImmutableList<StickerSetItem>,
        catalog: StickerCatalog?,
        adding: Adding,
    ): StickerSearch? {
        if (catalog == null) return null
        val needle = query.value.trim().lowercase(Locale.ROOT)
        val mine = installed.filter { it.matches(needle) }.toImmutableList()
        val mineIds = mine.mapTo(HashSet()) { it.id }
        val shown =
            when (catalog) {
                is StickerCatalog.Found -> {
                    StickerCatalog.Found(
                        catalog.sets
                            .filter { it.id !in mineIds }
                            .map {
                                it.copy(
                                    isInstalled = it.isInstalled || it.id in adding.added,
                                    isBusy = it.id in adding.busy,
                                )
                            }.toImmutableList(),
                    )
                }

                else -> {
                    catalog
                }
            }
        return StickerSearch(mine, shown)
    }

    /** The sets [busy] being added, and those [added] since the search ran. */
    data class Adding(
        val busy: Set<Long> = emptySet(),
        val added: Set<Long> = emptySet(),
    )

    private companion object {
        /** A pause after the last key before Telegram is asked, as global search waits. */
        const val SEARCH_DELAY_MILLIS = 300L
    }
}

/** The set's title or short name has [needle] (lowercase). */
private fun StickerSetItem.matches(needle: String): Boolean =
    title.lowercase(Locale.ROOT).contains(needle) || name.lowercase(Locale.ROOT).contains(needle)
