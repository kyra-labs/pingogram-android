package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The pinned messages page (the owner's design "Pinned messages page", `docs/DESIGN_SYSTEM.md` §6): the chat's title
 * ([chatTitle], null until known) and how many messages are pinned ([total]); the [filters] with their counts and the
 * [selected] one; the search's [query] while it is open ([searching]); the [content]; whether the user may unpin the
 * chat's messages ([canUnpin], which "Unpin all" and each card's unpin need), and the question before unpinning them
 * all
 * ([confirmUnpinAll]).
 */
@Immutable
data class PinnedPageUiState(
    val chatTitle: String? = null,
    val total: Int = 0,
    val filters: ImmutableList<PinnedFilterChip> = persistentListOf(),
    val selected: PinnedFilter = PinnedFilter.All,
    val searching: Boolean = false,
    val query: String = "",
    val content: PinnedPageContent = PinnedPageContent.Loading,
    val canUnpin: Boolean = false,
    val confirmUnpinAll: Boolean = false,
)

/** The kinds of pinned message the page filters by. */
enum class PinnedFilter {
    All,
    Text,
    Media,
    Files,
    Links,
}

/** A filter chip: its [filter] and how many pinned messages it holds. */
@Immutable
data class PinnedFilterChip(
    val filter: PinnedFilter,
    val count: Int,
)

/** What the page shows below its chips. */
sealed interface PinnedPageContent {
    data object Loading : PinnedPageContent

    /** No pinned message at all ([filtered] false), or none the filter or search finds. */
    data class Empty(
        val filtered: Boolean,
    ) : PinnedPageContent

    data class Error(
        val error: DomainError,
    ) : PinnedPageContent

    /** The pins, newest first, under their [months]. */
    data class Content(
        val months: ImmutableList<PinnedMonth>,
    ) : PinnedPageContent
}

/** The pins of one month ([label]: "November", or "November 2025" before this year), newest first. */
@Immutable
data class PinnedMonth(
    val label: String,
    val cards: ImmutableList<PinnedCard>,
)

/**
 * One pinned message: its [number] ("#23", counted from the oldest pin), its sender's [name], [avatar] in its
 * [avatarShape] and hue ([senderHue], `SenderHues`; -1 for the user's own, in `primary`), when it was sent
 * ([dateTime]), its whole [text] with its formatting (what kind of message it is, [kind], where it has nothing else to
 * show), and a [photo], [file] or [link] where it has one. [canCopy] where it has text and the chat lets its content be
 * saved.
 */
@Immutable
data class PinnedCard(
    val messageId: Long,
    val number: Int,
    val name: UiText,
    val avatar: SenderAvatar,
    val avatarShape: AvatarShape,
    val senderHue: Int,
    val dateTime: UiText,
    val text: FormattedText,
    val kind: UiText? = null,
    val photo: Photo? = null,
    val clip: PinnedClip? = null,
    val file: PinnedFile? = null,
    val link: PinnedLink? = null,
    val canCopy: Boolean = true,
) {
    override fun toString() = "PinnedCard(messageId=$messageId, number=$number, …)"
}

/** A pinned file: its [name] and its size in bytes ([size]), and what kind ([kind], e.g. "APK", "PDF"; may be empty).
* */
@Immutable
data class PinnedFile(
    val name: String,
    val size: Long,
    val kind: String,
) {
    override fun toString() = "PinnedFile(size=$size, …)"
}

/**
 * A pinned video, GIF or round video: its still picture (file [fileId], [width] × [height]), its length in [duration]
 * seconds, and whether it is a GIF ([isGif], labelled "GIF" rather than its length) or a round video ([isRound]).
 */
@Immutable
data class PinnedClip(
    val fileId: Int,
    val width: Int,
    val height: Int,
    val duration: Int,
    val isGif: Boolean = false,
    val isRound: Boolean = false,
)

/** A pinned message's link preview: its [host] ("play.google.com"), [title] and [description]; each may be empty. */
@Immutable
data class PinnedLink(
    val host: String,
    val title: String,
    val description: String,
)
