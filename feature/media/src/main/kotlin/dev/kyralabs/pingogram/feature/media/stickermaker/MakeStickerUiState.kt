package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Making a sticker from a photo (board 14a): the photo, how it is cropped, what is drawn on it, and the sheet that
 * adds the sticker to a set (board 14b) once [AddToSetState] is there.
 *
 * @property photo the photo's file, downloading or on the device.
 * @property crop how the photo sits in the sticker's square.
 * @property layers the text and emoji drawn on it, in the order they were added (the last on top).
 * @property selected the layer the user last touched, which shows its remove button; null for none.
 * @property outline whether the sticker has a white outline.
 * @property textDialog whether "Add text" asks for its text.
 * @property emojiFor what the emoji sheet chooses for, while it shows.
 * @property emojiGroups the emoji the sheet offers, read the first time it opens.
 * @property rendering while the drawing is written as a sticker.
 */
@Immutable
data class MakeStickerUiState(
    val photo: StickerPhoto = StickerPhoto.Loading(0f),
    val crop: StickerCrop = StickerCrop(),
    val layers: ImmutableList<StickerLayer> = persistentListOf(),
    val selected: Int? = null,
    val outline: Boolean = true,
    val textDialog: Boolean = false,
    val emojiFor: EmojiTarget? = null,
    val emojiGroups: ImmutableList<EmojiGroup> = persistentListOf(),
    val rendering: Boolean = false,
    val sheet: AddToSetState? = null,
) {
    /** "Next" draws the sticker: once the photo is there, and not while it is being written or the sheet shows. */
    val canFinish: Boolean get() = photo is StickerPhoto.Ready && !rendering && sheet == null
}

/** The photo the sticker is made of. */
@Immutable
sealed interface StickerPhoto {
    /** Downloading, [progress] from 0 to 1. */
    data class Loading(
        val progress: Float,
    ) : StickerPhoto

    /** On the device at [path]. */
    data class Ready(
        val path: String,
    ) : StickerPhoto {
        override fun toString() = "Ready(…)"
    }

    /** TDLib cannot download it. */
    data object Failed : StickerPhoto
}

/**
 * How the photo sits in the sticker's square: [zoom] times as large as when it just fills the square (1 to
 * [StickerMakerLimits.ZOOM_MAX]), moved by [panX] and [panY] from the centre, in fractions of the square's side.
 */
@Immutable
data class StickerCrop(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)

/**
 * Text or an emoji drawn on the sticker: [id] while the editor shows, [kind], its [content] (user content), centred at
 * [x] and [y], in fractions of the square's side from its top start.
 */
@Immutable
data class StickerLayer(
    val id: Int,
    val kind: LayerKind,
    val content: String,
    val x: Float = CENTRE,
    val y: Float = CENTRE,
) {
    override fun toString() = "StickerLayer(id=$id, kind=$kind, …)"

    private companion object {
        const val CENTRE = 0.5f
    }
}

/** What a layer draws. */
enum class LayerKind {
    /** White text with a dark edge, in the display style. */
    Text,

    /** An emoji, larger than text. */
    Emoji,
}

/** What the emoji sheet chooses for. */
enum class EmojiTarget {
    /** An emoji drawn on the sticker. */
    Layer,

    /** The sticker's own emoji, which Telegram suggests it for. */
    Sticker,
}

/** What the user does while making a sticker. */
sealed interface MakeStickerEvent {
    /** The photo was moved or zoomed in the square. */
    data class Cropped(
        val crop: StickerCrop,
    ) : MakeStickerEvent

    data object AddText : MakeStickerEvent

    /** The text dialog was answered with [text]; null when cancelled. */
    data class TextEntered(
        val text: String?,
    ) : MakeStickerEvent

    data object AddEmoji : MakeStickerEvent

    /** The emoji sheet chose [emoji]; null when it closed without one. */
    data class EmojiChosen(
        val emoji: String?,
    ) : MakeStickerEvent

    /** Layer [id] was touched; null to touch none. */
    data class Select(
        val id: Int?,
    ) : MakeStickerEvent

    /** Layer [id] was dragged to [x], [y]. */
    data class Moved(
        val id: Int,
        val x: Float,
        val y: Float,
    ) : MakeStickerEvent

    data class Remove(
        val id: Int,
    ) : MakeStickerEvent

    data object ToggleOutline : MakeStickerEvent

    /** "Next": the drawing is captured ([MakeStickerEffect.Render]). */
    data object Next : MakeStickerEvent

    /** The drawing, encoded as a PNG; null when it could not be captured. */
    data class Rendered(
        val image: ByteArray?,
    ) : MakeStickerEvent {
        override fun equals(other: Any?) = other is Rendered && image.contentEquals(other.image)

        override fun hashCode() = image.contentHashCode()

        override fun toString() = "Rendered(${image?.size} bytes)"
    }

    data object RetryPhoto : MakeStickerEvent

    /** What the sheet's user does: [AddToSetEvent]. */
    data class Sheet(
        val event: AddToSetEvent,
    ) : MakeStickerEvent
}

/** What [MakeStickerViewModel] asks of the screen once. */
sealed interface MakeStickerEffect {
    /** Capture the drawing and answer with [MakeStickerEvent.Rendered]. */
    data object Render : MakeStickerEffect

    /** Share [link], a sticker set's, through Android's share sheet. */
    data class ShareLink(
        val link: String,
    ) : MakeStickerEffect

    /** The sticker went into a set and the user is done: the screen closes. */
    data object Close : MakeStickerEffect

    /** A step failed: [error] in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : MakeStickerEffect
}

/** The editor's limits. */
object StickerMakerLimits {
    /** How far the photo zooms in, beyond filling the square. */
    const val ZOOM_MAX = 5f

    /** The most layers a sticker takes: enough for a caption and a few emoji, and a sticker is 512 px. */
    const val LAYER_COUNT_MAX = 10

    /** The longest text of a layer, in characters. */
    const val TEXT_LENGTH_MAX = 40

    /** The share of the square's side its corners round off. */
    const val CORNER_FRACTION = 0.12f

    /** The outline's width, a share of the square's side. */
    const val OUTLINE_FRACTION = 0.03f
}
