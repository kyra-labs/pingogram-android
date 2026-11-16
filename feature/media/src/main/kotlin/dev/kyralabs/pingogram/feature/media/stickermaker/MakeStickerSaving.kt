package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.lifecycle.SavedStateHandle
import kotlinx.collections.immutable.toImmutableList

/**
 * The editor in [handle], across process death: how the photo is cropped, the outline and the layers, each layer one
 * string of its kind, place and content apart by [SEPARATOR], which no text typed holds. The sheet is not kept: its
 * sticker is drawn again with "Next".
 */
internal class MakeStickerSaving(
    private val handle: SavedStateHandle,
) {
    /** [state] as it was saved, or as it is when nothing was. */
    fun restore(state: MakeStickerUiState): MakeStickerUiState {
        val crop = handle.get<FloatArray>(CROP)
        val layers =
            handle
                .get<ArrayList<String>>(LAYERS)
                .orEmpty()
                .mapIndexedNotNull { index, saved -> layerOf(index, saved) }
        return state.copy(
            crop = crop?.takeIf { it.size == CROP_SIZE }?.let { StickerCrop(it[0], it[1], it[2]) } ?: state.crop,
            outline = handle[OUTLINE] ?: state.outline,
            layers = layers.toImmutableList(),
        )
    }

    /** Keeps what [state] shows of the editor. */
    fun save(state: MakeStickerUiState) {
        handle[CROP] = floatArrayOf(state.crop.zoom, state.crop.panX, state.crop.panY)
        handle[OUTLINE] = state.outline
        handle[LAYERS] =
            ArrayList(
                state.layers.map { layer ->
                    listOf(layer.kind.name, layer.x.toString(), layer.y.toString(), layer.content)
                        .joinToString(SEPARATOR)
                },
            )
    }

    private fun layerOf(
        id: Int,
        saved: String,
    ): StickerLayer? {
        val parts = saved.split(SEPARATOR, limit = LAYER_PARTS).takeIf { it.size == LAYER_PARTS } ?: return null
        val kind = LayerKind.entries.firstOrNull { it.name == parts[0] }
        val x = parts[1].toFloatOrNull()
        val y = parts[2].toFloatOrNull()
        return kind?.let { k -> x?.let { px -> y?.let { py -> StickerLayer(id, k, parts[LAYER_PARTS - 1], px, py) } } }
    }

    private companion object {
        const val CROP = "makeSticker.crop"
        const val OUTLINE = "makeSticker.outline"
        const val LAYERS = "makeSticker.layers"
        const val CROP_SIZE = 3
        const val LAYER_PARTS = 4

        /** The unit separator: a control character, which [MakeStickerViewModel] takes out of typed text. */
        const val SEPARATOR = "\u001F"
    }
}
