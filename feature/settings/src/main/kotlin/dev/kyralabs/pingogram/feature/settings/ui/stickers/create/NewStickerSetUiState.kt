package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The steps of making a sticker set (the owner's boards 21a–c). */
enum class NewStickerSetStep { Name, Stickers, Published }

/**
 * What the page making a sticker set shows (`docs/DESIGN_SYSTEM.md` §6 "New sticker set").
 *
 * @property title the set's title as typed, and [name] its link name.
 * @property nameCheck what is known of [name]: being checked, free, taken, malformed, or the check's error.
 * @property stickers the stickers so far, in the set's order, and [preparing] how many picked photos are being made
 *   into stickers.
 * @property emojiFor the sticker whose emoji the sheet is choosing, by its index; null while it is closed.
 * @property menuFor the sticker whose menu is open, by its index.
 * @property isPublishing the set is on its way to Telegram; [error] why Telegram refused it.
 * @property published the set as published: its id.
 */
@Immutable
data class NewStickerSetUiState(
    val step: NewStickerSetStep = NewStickerSetStep.Name,
    val title: String = "",
    val name: String = "",
    val nameCheck: NameCheck = NameCheck.None,
    val stickers: ImmutableList<StickerTile> = persistentListOf(),
    val preparing: Int = 0,
    val emojiFor: Int? = null,
    val menuFor: Int? = null,
    val isPublishing: Boolean = false,
    val error: DomainError? = null,
    val published: Long? = null,
) {
    val canGoOn: Boolean get() = title.isNotBlank() && nameCheck == NameCheck.Available

    val canAddSticker: Boolean get() = !isPublishing && stickers.size + preparing < STICKER_COUNT_MAX

    val canPublish: Boolean get() = !isPublishing && stickers.isNotEmpty() && preparing == 0

    /** The link people open to add the set. */
    val link: String get() = "https://t.me/addstickers/$name"

    companion object {
        /** Telegram's most stickers in a set (`StickerSetLimits.STICKER_COUNT_MAX`). */
        const val STICKER_COUNT_MAX = 120
    }
}

/** A sticker of the set: its image's [path] on the device and its [emoji]. */
@Immutable
data class StickerTile(
    val path: String,
    val emoji: String,
)

/** What is known of the link name typed. */
@Immutable
sealed interface NameCheck {
    /** Nothing typed yet. */
    data object None : NameCheck

    data object Checking : NameCheck

    data object Available : NameCheck

    data object Occupied : NameCheck

    /** Not of the form Telegram takes. */
    data object Invalid : NameCheck

    data class Failed(
        val error: DomainError,
    ) : NameCheck
}

sealed interface NewStickerSetEvent {
    data class TitleChanged(
        val title: String,
    ) : NewStickerSetEvent

    data class NameChanged(
        val name: String,
    ) : NewStickerSetEvent

    /** "Next" from the name: the stickers come next. */
    data object Next : NewStickerSetEvent

    /** Back: from the stickers to the name, from the name out of the page. */
    data object Back : NewStickerSetEvent

    /** "Add sticker": the photo picker opens. */
    data object AddSticker : NewStickerSetEvent

    /** The photo picker's answer, a content URI. */
    data class PhotoPicked(
        val uri: String,
    ) : NewStickerSetEvent

    /** A sticker's tap: its emoji sheet opens. */
    data class ChooseEmoji(
        val index: Int,
    ) : NewStickerSetEvent

    data class EmojiChosen(
        val emoji: String,
    ) : NewStickerSetEvent

    data object CloseEmoji : NewStickerSetEvent

    /** A sticker's long press: its menu opens. */
    data class OpenMenu(
        val index: Int,
    ) : NewStickerSetEvent

    data object CloseMenu : NewStickerSetEvent

    /** Moves sticker [index] [by] places (-1 earlier, 1 later). */
    data class Move(
        val index: Int,
        val by: Int,
    ) : NewStickerSetEvent

    data class Remove(
        val index: Int,
    ) : NewStickerSetEvent

    data object Publish : NewStickerSetEvent
}

sealed interface NewStickerSetEffect {
    /** The photo picker opens, for images only. */
    data object PickPhoto : NewStickerSetEffect

    /** The page closes. */
    data object Close : NewStickerSetEffect

    /** The set was published: the `Confirm` haptic. */
    data object Published : NewStickerSetEffect

    data class Notice(
        @param:StringRes val text: Int,
    ) : NewStickerSetEffect

    data class ShowError(
        val error: DomainError,
    ) : NewStickerSetEffect
}
