package dev.kyralabs.pingogram.core.domain.model

/** A sticker of a set being made: its image as `StickerSetCreationRepository.prepareSticker` wrote it, its emoji. */
data class StickerDraft(
    val path: String,
    val emoji: String,
)

/** What Telegram says of a link name for a new sticker set (td_api.tl `checkStickerSetName`). */
enum class StickerSetNameCheck {
    Available,

    /** Not letters, digits and underscores starting with a letter, or not of a length Telegram takes. */
    Invalid,

    /** Another set has it. */
    Occupied,
}

/** The limits of a sticker set the user makes. */
object StickerSetLimits {
    /** The longest title, in characters. */
    const val TITLE_LENGTH_MAX = 64

    /** A link name's shortest and longest lengths, in characters. */
    const val NAME_LENGTH_MIN = 5
    const val NAME_LENGTH_MAX = 64

    /** The most stickers a set holds. */
    const val STICKER_COUNT_MAX = 120

    /** A static sticker's longer side, in pixels: Telegram takes exactly this. */
    const val STICKER_SIDE_PX = 512

    /** Whether [name] has the form Telegram takes for a link name: a letter, then letters, digits and underscores. */
    fun isNameWellFormed(name: String): Boolean =
        name.length in NAME_LENGTH_MIN..NAME_LENGTH_MAX &&
            name.first().isAsciiLetter() &&
            name.all { it.isAsciiLetter() || it in '0'..'9' || it == '_' } &&
            !name.contains("__") &&
            !name.endsWith('_')

    private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'
}
