package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import kotlinx.collections.immutable.ImmutableList

/**
 * The topic list of a forum (`REQUIREMENTS.md` FR-C9): the forum in the top bar ([header], null until it is known) and
 * its topics ([content]).
 */
@Immutable
data class ForumTopicsUiState(
    val header: ChatHeader? = null,
    val content: TopicsContent = TopicsContent.Loading,
)

/** The topics, or why there are none. */
@Immutable
sealed interface TopicsContent {
    /** The first page is on its way. */
    data object Loading : TopicsContent

    /** The forum has no topic the user can see. */
    data object Empty : TopicsContent

    /** Loading the first page failed with [error]: shown with a retry ([ForumTopicsEvent.Retry]). */
    data class Error(
        val error: DomainError,
    ) : TopicsContent

    /**
     * The topics loaded so far, in the forum's order; more load near the end until [reachedEnd], with a progress
     * indicator ([isLoadingMore]) or the error of the next page ([moreError]) below the last one.
     */
    data class Content(
        val topics: ImmutableList<TopicRow>,
        val reachedEnd: Boolean,
        val isLoadingMore: Boolean = false,
        val moreError: DomainError? = null,
    ) : TopicsContent
}

/**
 * One topic in the list: its [name] and [icon], one line of its last message ([sender] before [preview]; no sender
 * for the user's own or the forum's), its [time], and its unread and mention counts, pinned and closed.
 */
@Immutable
data class TopicRow(
    val id: Int,
    val name: String,
    val icon: TopicIcon,
    val sender: UiText?,
    val preview: UiText?,
    val time: String?,
    val unreadCount: Int,
    val unreadMentionCount: Int,
    val isPinned: Boolean,
    val isClosed: Boolean,
) {
    override fun toString() = "TopicRow(id=$id, unreadCount=$unreadCount, isPinned=$isPinned, isClosed=$isClosed, …)"
}

/** A topic's icon: the General topic's "#", or a topic's bubble in the tone of its [color]. */
@Immutable
data class TopicIcon(
    val color: TopicColor,
    val isGeneral: Boolean,
)
