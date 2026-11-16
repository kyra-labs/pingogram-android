package dev.kyralabs.pingogram.feature.media.stickermaker

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetLimits
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
import dev.kyralabs.pingogram.core.domain.repository.StickerMakerRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSetCreationRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the sheet reaches: the user's own sets and adding to them, and making a new set. */
internal class StickerSets(
    val maker: StickerMakerRepository,
    val creation: StickerSetCreationRepository,
)

/**
 * The sheet that adds the sticker just made to a set (board 14b), a part of [MakeStickerViewModel]: it reads the
 * user's own sets as it opens, choosing the first (or a new set where there is none), adds the sticker to the chosen
 * one, or makes a new set of it ([NewSetNaming]). The sticker's copy is discarded once Telegram has it, or the sheet
 * closes without; [openEmoji] opens the emoji sheet for the sticker's emoji.
 */
internal class AddToSet(
    private val sources: StickerSets,
    private val state: MutableStateFlow<MakeStickerUiState>,
    private val scope: CoroutineScope,
    private val effect: (MakeStickerEffect) -> Unit,
    private val openEmoji: () -> Unit,
) {
    private val naming = NewSetNaming(sources.creation, state, scope)

    /** Opens the sheet for the sticker written at [path]. */
    fun open(path: String) {
        state.update { it.copy(rendering = false, sheet = AddToSetState(path)) }
        load()
    }

    fun onEvent(event: AddToSetEvent) {
        val sheet = state.value.sheet ?: return
        if (sheet.busy && event != AddToSetEvent.Dismiss) return
        when (event) {
            is AddToSetEvent.Emoji, is AddToSetEvent.Choose, AddToSetEvent.Back, AddToSetEvent.MoreEmoji -> {
                choose(
                    event,
                )
            }

            is AddToSetEvent.Title, is AddToSetEvent.Name -> {
                naming.edit(event)
            }

            AddToSetEvent.Confirm -> {
                confirm(sheet)
            }

            AddToSetEvent.Create -> {
                create(sheet)
            }

            AddToSetEvent.RetrySets -> {
                load()
            }

            AddToSetEvent.Share, AddToSetEvent.Done, AddToSetEvent.Dismiss -> {
                leave(event, sheet)
            }
        }
    }

    /** The emoji, the set, or back from a new set to choosing. */
    private fun choose(event: AddToSetEvent) {
        when (event) {
            is AddToSetEvent.Emoji -> state.updateSheet { it.copy(emoji = event.emoji) }
            is AddToSetEvent.Choose -> state.updateSheet { it.copy(choice = event.name, error = null) }
            AddToSetEvent.Back -> state.updateSheet { it.copy(step = AddToSetStep.Choose, error = null) }
            else -> openEmoji()
        }
    }

    /** Sharing the set's link, "Done", or the sheet closed. */
    private fun leave(
        event: AddToSetEvent,
        sheet: AddToSetState,
    ) {
        when (event) {
            AddToSetEvent.Share -> sheet.added?.let { effect(MakeStickerEffect.ShareLink(LINK_PREFIX + it.name)) }
            AddToSetEvent.Done -> effect(MakeStickerEffect.Close)
            else -> close(sheet)
        }
    }

    /** The sheet left without the sticker going anywhere: its copy goes, and the editor shows again. */
    fun close(sheet: AddToSetState? = state.value.sheet) {
        naming.cancel()
        if (sheet == null) return
        if (sheet.step != AddToSetStep.Done) sources.maker.discardSticker(sheet.stickerPath)
        state.update { it.copy(sheet = null) }
    }

    private fun load() {
        state.updateSheet { it.copy(sets = OwnedSets.Loading) }
        scope.launch {
            val sets =
                when (val owned = sources.maker.ownedSets()) {
                    is Outcome.Failure -> {
                        OwnedSets.Failed(owned.error)
                    }

                    is Outcome.Success -> {
                        OwnedSets.Loaded(
                            owned.value
                                .map { OwnedSet(it.title, it.name, it.size, it.cover) }
                                .toImmutableList(),
                        )
                    }
                }
            state.updateSheet { sheet ->
                val first = (sets as? OwnedSets.Loaded)?.sets?.firstOrNull()?.name ?: AddToSetState.NEW_SET
                sheet.copy(sets = sets, choice = sheet.choice ?: first.takeIf { sets is OwnedSets.Loaded })
            }
        }
    }

    private fun confirm(sheet: AddToSetState) {
        val set = sheet.chosenSet
        when {
            set != null -> add(sheet, set)
            sheet.choice == AddToSetState.NEW_SET -> state.updateSheet { it.copy(step = AddToSetStep.NewSet) }
        }
    }

    private fun add(
        sheet: AddToSetState,
        set: OwnedSet,
    ) {
        state.updateSheet { it.copy(busy = true, error = null) }
        scope.launch {
            val added = sources.maker.addSticker(set.name, StickerDraft(sheet.stickerPath, sheet.emoji))
            finish(added, set.copy(size = set.size + 1))
        }
    }

    private fun create(sheet: AddToSetState) {
        val draft = sheet.newSet
        if (draft.check != StickerSetNameCheck.Available || draft.title.isBlank()) return
        naming.cancel()
        state.updateSheet { it.copy(busy = true, error = null) }
        scope.launch {
            val sticker = StickerDraft(sheet.stickerPath, sheet.emoji)
            val made = sources.creation.create(draft.title, draft.name, listOf(sticker))
            finish(made, OwnedSet(draft.title.trim(), draft.name, size = 1, cover = null))
        }
    }

    /** Telegram answered: the sheet shows the set the sticker went into, or why it did not. */
    private fun finish(
        outcome: Outcome<*>,
        set: OwnedSet,
    ) {
        when (outcome) {
            is Outcome.Failure -> {
                state.updateSheet { it.copy(busy = false, error = outcome.error) }
            }

            is Outcome.Success -> {
                state.value.sheet?.let { sources.maker.discardSticker(it.stickerPath) }
                state.updateSheet { it.copy(busy = false, step = AddToSetStep.Done, added = set) }
            }
        }
    }

    private companion object {
        const val LINK_PREFIX = "https://t.me/addstickers/"
    }
}

/**
 * A new set's name and link, a part of [AddToSet]: the link is Telegram's suggestion for the name until the user types
 * one, and what Telegram says of it is asked [CHECK_DELAY] after the last change, the newest change cancelling the
 * check before it.
 */
internal class NewSetNaming(
    private val creation: StickerSetCreationRepository,
    private val state: MutableStateFlow<MakeStickerUiState>,
    private val scope: CoroutineScope,
) {
    private var check: Job? = null

    /** The name ([AddToSetEvent.Title]) or the link ([AddToSetEvent.Name]) was typed. */
    fun edit(event: AddToSetEvent) {
        if (event is AddToSetEvent.Title) {
            val title = event.title.take(StickerSetLimits.TITLE_LENGTH_MAX)
            state.updateSheet { it.copy(newSet = it.newSet.copy(title = title), error = null) }
            val linkTyped =
                state.value.sheet
                    ?.newSet
                    ?.nameTyped == true
            if (!linkTyped) checkLater(suggest = true)
        } else if (event is AddToSetEvent.Name) {
            val name = event.name.trim().take(StickerSetLimits.NAME_LENGTH_MAX)
            state.updateSheet {
                it.copy(newSet = it.newSet.copy(name = name, nameTyped = true, check = null), error = null)
            }
            checkLater(suggest = false)
        }
    }

    fun cancel() {
        check?.cancel()
    }

    /** Asks Telegram for a link for the name where [suggest], then whether the link is free, after a pause. */
    private fun checkLater(suggest: Boolean) {
        check?.cancel()
        state.updateSheet { it.copy(newSet = it.newSet.copy(check = null)) }
        check =
            scope.launch {
                delay(CHECK_DELAY)
                val draft = state.value.sheet?.newSet ?: return@launch
                val name =
                    if (suggest && draft.title.isNotBlank()) {
                        (creation.suggestName(draft.title) as? Outcome.Success)?.value ?: draft.name
                    } else {
                        draft.name
                    }
                val answer = (creation.checkName(name) as? Outcome.Success)?.value
                state.updateSheet { it.copy(newSet = it.newSet.copy(name = name, check = answer)) }
            }
    }

    private companion object {
        const val CHECK_DELAY = 500L
    }
}

/** Changes the sheet with [change], while it shows. */
internal inline fun MutableStateFlow<MakeStickerUiState>.updateSheet(
    crossinline change: (AddToSetState) -> AddToSetState,
) {
    update { ui -> ui.sheet?.let { ui.copy(sheet = change(it)) } ?: ui }
}
