package dev.kyralabs.pingogram.feature.settings.ui.stickers.set

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.StickerSetRepository
import dev.kyralabs.pingogram.core.navigation.StickerSetRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The sheet of sticker set [setId], or of the set named [name] when it came from a link (`t.me/addstickers/<name>`,
 * found with `StickerSetRepository.findSet`): its stickers from `StickerSetRepository.stickerSet`, read again
 * whenever it changes, here or on another device, and "Add stickers" or "Remove", which change it only on a tap.
 *
 * Nothing needs restoring after process death: the set is in the route, and the sheet reads it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StickerSetSheetViewModel internal constructor(
    private val setId: Long,
    private val name: String?,
    private val repository: StickerSetRepository,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: StickerSetRepository,
    ) : this(
        savedStateHandle.toRoute<StickerSetRoute>().setId,
        savedStateHandle.toRoute<StickerSetRoute>().name,
        repository,
    )

    private val mutableUiState = MutableStateFlow<StickerSetSheetUiState>(StickerSetSheetUiState.Loading)
    val uiState: StateFlow<StickerSetSheetUiState> = mutableUiState.asStateFlow()

    private val retries = MutableStateFlow(0)

    /** The set's id once known: the route's, or the one its name led to. */
    private var resolvedId: Long? = setId.takeIf { it != 0L }

    init {
        // After the sheet's first frame (docs/PERFORMANCE.md §4).
        viewModelScope.launch(Dispatchers.Main) {
            retries
                .flatMapLatest {
                    when (val id = resolve()) {
                        is Outcome.Success -> repository.stickerSet(id.value)
                        is Outcome.Failure -> flowOf(id)
                    }
                }.collectLatest { outcome ->
                    when (outcome) {
                        is Outcome.Success -> {
                            val details = outcome.value
                            mutableUiState.update { state ->
                                val previous = state as? StickerSetSheetUiState.Content
                                StickerSetSheetUiState.Content(
                                    title = details.set.title,
                                    name = details.set.name,
                                    stickers = details.stickers.toImmutableList(),
                                    isInstalled = details.set.isInstalled,
                                    isArchived = details.set.isArchived,
                                    // A change under way ends with the read that shows it.
                                    isChanging =
                                        previous?.isChanging == true && previous.isInstalled == details.set.isInstalled,
                                    changeError = previous?.changeError,
                                    kind = details.set.kind,
                                )
                            }
                        }

                        is Outcome.Failure -> {
                            if (uiState.value !is StickerSetSheetUiState.Content) {
                                mutableUiState.value =
                                    StickerSetSheetUiState.Failed(outcome.error, outcome.error.canRetry())
                            }
                        }
                    }
                }
        }
    }

    fun onEvent(event: StickerSetSheetEvent) {
        when (event) {
            StickerSetSheetEvent.Retry -> {
                if (uiState.value is StickerSetSheetUiState.Failed) {
                    mutableUiState.value = StickerSetSheetUiState.Loading
                    retries.update { it + 1 }
                }
            }

            StickerSetSheetEvent.Add -> {
                change(install = true)
            }

            StickerSetSheetEvent.Remove -> {
                change(install = false)
            }
        }
    }

    private suspend fun resolve(): Outcome<Long> {
        resolvedId?.let { return Outcome.Success(it) }
        val found = name?.let { repository.findSet(it) } ?: Outcome.Failure(DomainError.NotFound)
        if (found is Outcome.Success) resolvedId = found.value
        return found
    }

    private fun change(install: Boolean) {
        // Only a set on screen, with its id known, not changing already, and not in the state asked for.
        val content =
            (uiState.value as? StickerSetSheetUiState.Content)?.takeIf { !it.isChanging && it.isInstalled != install }
        val id = resolvedId
        if (content == null || id == null) return
        mutableUiState.value = content.copy(isChanging = true, changeError = null)
        viewModelScope.launch {
            val outcome = if (install) repository.install(id) else repository.remove(id)
            if (outcome is Outcome.Failure) {
                mutableUiState.update { state ->
                    (state as? StickerSetSheetUiState.Content)?.copy(isChanging = false, changeError = outcome.error)
                        ?: state
                }
            }
        }
    }
}

/** Whether reading the set again may help: no network, a flood wait; not for a set that does not exist. */
private fun DomainError.canRetry(): Boolean = this == DomainError.Network || this is DomainError.FloodWait
