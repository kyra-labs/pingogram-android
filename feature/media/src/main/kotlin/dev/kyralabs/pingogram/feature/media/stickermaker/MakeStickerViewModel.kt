package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.repository.EmojiCatalogRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerMakerRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSetCreationRepository
import dev.kyralabs.pingogram.core.navigation.MakeStickerRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Making a sticker from the photo of the route's file (boards 14a, 14b). The photo downloads at visible priority as
 * the screen opens; the user moves and zooms it in the sticker's square, adds text and emoji ([StickerMakerLimits]),
 * and turns the outline on or off, all kept across process death ([MakeStickerSaving]). "Next" has the screen capture
 * its drawing ([MakeStickerEffect.Render]), which is written as a sticker on the device, and opens the sheet that adds
 * it to a set ([AddToSet]). Nothing reaches Telegram before the sheet's "Add to …" or "Create set".
 */
@HiltViewModel
class MakeStickerViewModel internal constructor(
    route: MakeStickerRoute,
    handle: SavedStateHandle,
    private val files: FileRepository,
    private val sets: StickerSets,
    private val emoji: EmojiCatalogRepository,
) : ViewModel() {
    @Inject
    constructor(
        handle: SavedStateHandle,
        files: FileRepository,
        maker: StickerMakerRepository,
        creation: StickerSetCreationRepository,
        emoji: EmojiCatalogRepository,
    ) : this(handle.toRoute<MakeStickerRoute>(), handle, files, StickerSets(maker, creation), emoji)

    /** The photo's file, which the screen draws. */
    internal val fileId = route.fileId
    private val saving = MakeStickerSaving(handle)
    private val state = MutableStateFlow(saving.restore(MakeStickerUiState()))
    val uiState: StateFlow<MakeStickerUiState> = state.asStateFlow()

    private val effectChannel = Channel<MakeStickerEffect>(Channel.BUFFERED)
    val effects: Flow<MakeStickerEffect> = effectChannel.receiveAsFlow()

    private val addToSet =
        AddToSet(sets, state, viewModelScope, effectChannel::trySend) { openEmoji(EmojiTarget.Sticker) }
    private var nextId = (state.value.layers.maxOfOrNull { it.id } ?: -1) + 1
    private var photo: Job? = null

    init {
        // After the screen's first frame: the view model is made while it composes (docs/PERFORMANCE.md §4).
        viewModelScope.launch { download() }
    }

    fun onEvent(event: MakeStickerEvent) {
        when (event) {
            is MakeStickerEvent.Cropped -> edit { it.copy(crop = event.crop.coerced()) }
            MakeStickerEvent.AddText -> state.update { it.copy(textDialog = it.layers.size < LAYERS_MAX) }
            is MakeStickerEvent.TextEntered -> textEntered(event.text)
            MakeStickerEvent.AddEmoji -> openEmoji(EmojiTarget.Layer)
            is MakeStickerEvent.EmojiChosen -> emojiChosen(event.emoji)
            is MakeStickerEvent.Select -> state.update { it.copy(selected = event.id) }
            is MakeStickerEvent.Moved -> edit { ui -> ui.copy(layers = ui.layers.moved(event)) }
            is MakeStickerEvent.Remove -> edit { ui -> ui.copy(layers = ui.layers.without(event.id), selected = null) }
            MakeStickerEvent.ToggleOutline -> edit { it.copy(outline = !it.outline) }
            MakeStickerEvent.Next -> next()
            is MakeStickerEvent.Rendered -> rendered(event.image)
            MakeStickerEvent.RetryPhoto -> viewModelScope.launch { download() }
            is MakeStickerEvent.Sheet -> addToSet.onEvent(event.event)
        }
    }

    private suspend fun download() {
        photo?.cancel()
        state.update { it.copy(photo = StickerPhoto.Loading(0f)) }
        if (files.download(fileId, DownloadPriority.Visible) is Outcome.Failure) {
            state.update { it.copy(photo = StickerPhoto.Failed) }
            return
        }
        photo =
            viewModelScope.launch {
                // Until the photo is on the device: then nothing about its file changes what the editor shows.
                files
                    .observe(fileId)
                    .transformWhile { file ->
                        emit(file)
                        file !is FileState.Downloaded
                    }.collect { file ->
                        val shown =
                            when (file) {
                                is FileState.Downloaded -> StickerPhoto.Ready(file.path)
                                is FileState.Downloading -> StickerPhoto.Loading(file.progress)
                                is FileState.NotDownloaded -> StickerPhoto.Failed.takeUnless { file.canBeDownloaded }
                            }
                        shown?.let { photo -> state.update { it.copy(photo = photo) } }
                    }
            }
    }

    private fun textEntered(text: String?) {
        val clean =
            text
                .orEmpty()
                .filterNot(Char::isISOControl)
                .trim()
                .take(StickerMakerLimits.TEXT_LENGTH_MAX)
        state.update { it.copy(textDialog = false) }
        if (clean.isNotEmpty()) addLayer(LayerKind.Text, clean)
    }

    private fun openEmoji(target: EmojiTarget) {
        if (target == EmojiTarget.Layer && state.value.layers.size >= LAYERS_MAX) return
        state.update { it.copy(emojiFor = target) }
        if (state.value.emojiGroups.isEmpty()) {
            viewModelScope.launch {
                val groups = emoji.catalog().filter { it.emoji.isNotEmpty() }.toImmutableList()
                state.update { it.copy(emojiGroups = groups) }
            }
        }
    }

    private fun emojiChosen(chosen: String?) {
        val target = state.value.emojiFor
        state.update { it.copy(emojiFor = null) }
        if (chosen == null) return
        when (target) {
            EmojiTarget.Layer -> addLayer(LayerKind.Emoji, chosen)
            EmojiTarget.Sticker -> state.updateSheet { it.copy(emoji = chosen) }
            null -> Unit
        }
    }

    private fun addLayer(
        kind: LayerKind,
        content: String,
    ) {
        if (state.value.layers.size >= LAYERS_MAX) return
        val layer = StickerLayer(nextId++, kind, content)
        edit { it.copy(layers = (it.layers + layer).toImmutableList(), selected = layer.id) }
    }

    private fun next() {
        if (!state.value.canFinish) return
        // The selection's handles are not part of the drawing, but the touched layer's frame would be.
        state.update { it.copy(rendering = true, selected = null) }
        effectChannel.trySend(MakeStickerEffect.Render)
    }

    private fun rendered(image: ByteArray?) {
        if (!state.value.rendering) return
        viewModelScope.launch {
            when (
                val written =
                    image?.let { sets.maker.prepareSticker(it) } ?: Outcome.Failure(DomainError.PhotoInvalid)
            ) {
                is Outcome.Failure -> {
                    state.update { it.copy(rendering = false) }
                    effectChannel.trySend(MakeStickerEffect.ShowError(written.error))
                }

                is Outcome.Success -> {
                    addToSet.open(written.value)
                }
            }
        }
    }

    /** Changes the editor with [change] and keeps it ([MakeStickerSaving]). */
    private inline fun edit(change: (MakeStickerUiState) -> MakeStickerUiState) {
        state.update(change)
        saving.save(state.value)
    }

    override fun onCleared() {
        // A sticker written but added nowhere goes with the screen.
        addToSet.close()
    }

    private companion object {
        const val LAYERS_MAX = StickerMakerLimits.LAYER_COUNT_MAX
    }
}

/**
 * [this] within what the editor allows: zoomed in at most [StickerMakerLimits.ZOOM_MAX] times, never out, and moved no
 * further than the screen lets it ([StickerMakerLimits.ZOOM_MAX] halves of the square), which knows the photo's shape.
 */
internal fun StickerCrop.coerced(): StickerCrop {
    val room = StickerMakerLimits.ZOOM_MAX / 2
    return StickerCrop(
        zoom.coerceIn(1f, StickerMakerLimits.ZOOM_MAX),
        panX.coerceIn(-room, room),
        panY.coerceIn(-room, room),
    )
}

private fun List<StickerLayer>.moved(event: MakeStickerEvent.Moved) =
    map { if (it.id == event.id) it.copy(x = event.x.coerceIn(0f, 1f), y = event.y.coerceIn(0f, 1f)) else it }
        .toImmutableList()

private fun List<StickerLayer>.without(id: Int) = filterNot { it.id == id }.toImmutableList()
