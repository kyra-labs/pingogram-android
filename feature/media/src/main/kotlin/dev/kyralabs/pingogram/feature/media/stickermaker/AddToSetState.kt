package dev.kyralabs.pingogram.feature.media.stickermaker

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The sheet that adds the sticker just made to a set (board 14b).
 *
 * @property stickerPath the sticker, written on the device.
 * @property emoji the emoji Telegram suggests it for.
 * @property sets the user's own sets that can take it.
 * @property choice the set it goes into: one of [sets] by its link name, or [NEW_SET] for a new one; null while the
 *   sets load.
 * @property step what the sheet shows.
 * @property newSet a new set's name and link, while [step] is [AddToSetStep.NewSet].
 * @property busy while Telegram adds the sticker or makes the set.
 * @property error why the last change failed, a polite live region.
 * @property added the set the sticker went into, once [step] is [AddToSetStep.Done].
 */
@Immutable
data class AddToSetState(
    val stickerPath: String,
    val emoji: String = QUICK_EMOJI.first(),
    val sets: OwnedSets = OwnedSets.Loading,
    val choice: String? = null,
    val step: AddToSetStep = AddToSetStep.Choose,
    val newSet: NewSetDraft = NewSetDraft(),
    val busy: Boolean = false,
    val error: DomainError? = null,
    val added: OwnedSet? = null,
) {
    /** The set chosen, while it is one of the user's. */
    val chosenSet: OwnedSet?
        get() = (sets as? OwnedSets.Loaded)?.sets?.firstOrNull { it.name == choice }

    override fun toString() = "AddToSetState(step=$step, choice=${choice != null}, busy=$busy)"

    companion object {
        /** [choice] for a new set. */
        const val NEW_SET = ""

        /** The emoji offered first, as the board does; "More" opens all of them. */
        val QUICK_EMOJI = persistentListOf("😀", "😂", "❤️", "👍", "😴")
    }
}

/** The user's own sets the sticker can join. */
@Immutable
sealed interface OwnedSets {
    data object Loading : OwnedSets

    data class Loaded(
        val sets: ImmutableList<OwnedSet>,
    ) : OwnedSets

    data class Failed(
        val error: DomainError,
    ) : OwnedSets
}

/** One of the user's own sets: its [title], link [name], how many stickers it holds and its first, its [cover]. */
@Immutable
data class OwnedSet(
    val title: String,
    val name: String,
    val size: Int,
    val cover: Sticker?,
) {
    override fun toString() = "OwnedSet(size=$size)"
}

/** What the sheet shows. */
enum class AddToSetStep {
    /** The emoji and the set. */
    Choose,

    /** A new set's name and link. */
    NewSet,

    /** The sticker is in its set. */
    Done,
}

/**
 * A new set: its [title], its link [name] (Telegram's suggestion for the title until the user types one,
 * [nameTyped]), and what Telegram says of the name ([check]; null while it is asked).
 */
@Immutable
data class NewSetDraft(
    val title: String = "",
    val name: String = "",
    val nameTyped: Boolean = false,
    val check: StickerSetNameCheck? = null,
) {
    override fun toString() = "NewSetDraft(check=$check)"
}

/** What the user does in the sheet. */
sealed interface AddToSetEvent {
    /** [emoji] chosen among the quick ones. */
    data class Emoji(
        val emoji: String,
    ) : AddToSetEvent

    /** "More": the emoji sheet opens for the sticker. */
    data object MoreEmoji : AddToSetEvent

    /** Set [name] chosen, or a new one for [AddToSetState.NEW_SET]. */
    data class Choose(
        val name: String,
    ) : AddToSetEvent

    /** "Add to …", or "Next" for a new set. */
    data object Confirm : AddToSetEvent

    data class Title(
        val title: String,
    ) : AddToSetEvent

    data class Name(
        val name: String,
    ) : AddToSetEvent

    /** "Create set". */
    data object Create : AddToSetEvent

    /** Back from the new set's step to choosing. */
    data object Back : AddToSetEvent

    data object RetrySets : AddToSetEvent

    /** "Share link" once added. */
    data object Share : AddToSetEvent

    /** "Done" once added: the screen closes. */
    data object Done : AddToSetEvent

    /** The scrim, dragging or back closed the sheet: the editor shows again. */
    data object Dismiss : AddToSetEvent
}
