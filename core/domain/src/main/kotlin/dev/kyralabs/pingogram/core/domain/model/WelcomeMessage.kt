package dev.kyralabs.pingogram.core.domain.model

/**
 * A group's welcome message (td_api.tl `welcomeMessage`): what each new member sees once, right after joining. A
 * message is a text, or a photo with an optional caption ([text]); one of another kind, which another app set, is
 * [isSupported] false, so it can be deleted but not shown or edited.
 */
data class WelcomeMessage(
    val id: Int,
    val text: FormattedText,
    val photo: Photo? = null,
    val isSupported: Boolean = true,
)

/**
 * A welcome message to add or edit: its [text] (the caption of a [photo]), and its photo, if any. A text without
 * a photo must not be blank (`DomainError.InputRejected`).
 */
data class WelcomeDraft(
    val text: FormattedText,
    val photo: WelcomePhoto? = null,
)

/** The photo of a [WelcomeDraft]: one the user just picked, or the one the message has, kept. */
sealed interface WelcomePhoto {
    /** A photo `PickedPhotoRepository.preparePhoto` copied to [path]; the caller discards it once TDLib answered. */
    data class Picked(
        val path: String,
    ) : WelcomePhoto

    /** The message's own [photo], kept as it is. */
    data class Kept(
        val photo: Photo,
    ) : WelcomePhoto
}

/** How many welcome messages a group may have. */
object WelcomeLimits {
    /** Telegram's limit while TDLib does not say otherwise (its option `welcome_message_count_max`). */
    const val COUNT_MAX_DEFAULT = 5
}
