package dev.kyralabs.pingogram.feature.chat.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.topics.ForumTopicsUiState
import dev.kyralabs.pingogram.feature.chat.ui.topics.TopicActions
import dev.kyralabs.pingogram.feature.chat.ui.topics.TopicDeleteUiState
import dev.kyralabs.pingogram.feature.chat.ui.topics.TopicEditorUiState
import dev.kyralabs.pingogram.feature.chat.ui.topics.TopicIcon
import dev.kyralabs.pingogram.feature.chat.ui.topics.TopicMenuState
import dev.kyralabs.pingogram.feature.chat.ui.topics.TopicRow
import dev.kyralabs.pingogram.feature.chat.ui.topics.TopicsContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * A forum's topic list for previews and screenshot tests: "Compose Developers" with the General topic, a pinned one,
 * unread ones (one with a mention), a closed one, and the loading, empty and error states; as an administrator who
 * manages the topics sees it, with the menus, the editor and the question before deleting.
 */
internal object TopicsPreviewData {
    private const val FORUM = -1_000_000_000_300L

    /** "Material 3 Expressive", which the administrator's list shows pinned too. */
    private const val EXPRESSIVE_TOPIC = 9

    val header =
        ChatHeader(
            chatId = FORUM,
            title = UiText.Plain("Compose Developers"),
            photo = null,
            placeholderIcon = null,
            subtitle = ChatSubtitle.Status(UiText.plural(ChatR.plurals.topics_subtitle, 6, 6)),
        )

    val topics =
        persistentListOf(
            topic(
                1,
                "General",
                TopicColor.Blue,
                "Grace",
                "Welcome! Introduce yourself here",
                "9:41 AM",
                general = true,
            ),
            topic(5, "Announcements", TopicColor.Red, "Ada", "Meetup moved to Thursday", "9:12 AM", pinned = true),
            topic(
                9,
                "Material 3 Expressive",
                TopicColor.Violet,
                "Linus",
                "The new loading indicator looks great",
                "8:30 AM",
                unread = 12,
                mentions = 1,
            ),
            topic(12, "Help", TopicColor.Green, null, "How do I animate a shape morph?", "Yesterday", unread = 3),
            topic(14, "Releases", TopicColor.Yellow, "Alan", "1.5.0-beta01 is out", "Oct 12", closed = true),
            topic(20, "Off-topic", TopicColor.Rose, "Ada", "Photo", "Sep 30"),
        )

    val content = ForumTopicsUiState(header, TopicsContent.Content(topics, reachedEnd = true))

    val loadingMore =
        ForumTopicsUiState(header, TopicsContent.Content(topics, reachedEnd = false, isLoadingMore = true))

    val loading = ForumTopicsUiState(header.copy(subtitle = null), TopicsContent.Loading)

    val empty = ForumTopicsUiState(header, TopicsContent.Empty)

    val error = ForumTopicsUiState(header, TopicsContent.Error(DomainError.Network))

    /** What an administrator who manages topics gets: every topic's menu. */
    private val adminActions = TopicActions(canEdit = true, canClose = true, canPin = true, canDelete = true)

    private val generalActions = TopicActions(canEdit = true, canHideGeneral = true)

    /**
     * The list as an administrator who manages topics sees it: "New topic", a menu on every topic, the hidden General
     * topic, two pinned topics to reorder.
     */
    val managed =
        ForumTopicsUiState(
            header = header,
            content =
                TopicsContent.Content(
                    topics =
                        topics
                            .map { row ->
                                val general = row.icon.isGeneral
                                val pinned = row.isPinned || row.id == EXPRESSIVE_TOPIC
                                val managedRow = row.copy(isPinned = pinned, isHidden = general)
                                managedRow.copy(menu = managedRow.menu(if (general) generalActions else adminActions))
                            }.toImmutableList(),
                    reachedEnd = true,
                    canReorder = true,
                ),
            canCreate = true,
        )

    /** The menu of the "Help" topic for an administrator who manages topics. */
    val helpMenu = managed.rowMenu(12)

    /** The topic conversation's menu of a closed topic. */
    val closedMenu = managed.rowMenu(14)

    /** The editor of a new topic in [color]. */
    fun newTopic(color: TopicColor = TopicColor.Blue) =
        TopicEditorUiState(topicId = null, name = "Plans for 2027", color = color)

    val renaming = TopicEditorUiState(topicId = 12, name = "Help", color = TopicColor.Green)

    val refused = newTopic(TopicColor.Rose).copy(error = DomainError.FloodWait(42))

    val saving = renaming.copy(name = "Help desk", isSaving = true)

    val deleting = TopicDeleteUiState(12, "Help")

    private fun TopicRow.menu(actions: TopicActions) =
        TopicMenuState(id, name, icon.isGeneral, isClosed, isPinned, isHidden, actions)

    private fun ForumTopicsUiState.rowMenu(id: Int): TopicMenuState =
        checkNotNull(((content as TopicsContent.Content).topics.first { it.id == id }).menu)

    @Suppress("LongParameterList") // Every part of a row, most with defaults.
    private fun topic(
        id: Int,
        name: String,
        color: TopicColor,
        sender: String?,
        text: String,
        time: String,
        general: Boolean = false,
        pinned: Boolean = false,
        unread: Int = 0,
        mentions: Int = 0,
        closed: Boolean = false,
    ) = TopicRow(
        id = id,
        name = name,
        icon = TopicIcon(color, general),
        sender = sender?.let { UiText.Plain(it).bidiIsolated() } ?: UiText.resource(R.string.preview_sender_you),
        preview = UiText.Plain(text),
        time = time,
        unreadCount = unread,
        unreadMentionCount = mentions,
        isPinned = pinned,
        isClosed = closed,
    )
}

/** Every state of the topic list, for its previews. */
internal class TopicsPreviewStates : PreviewParameterProvider<ForumTopicsUiState> {
    override val values: Sequence<ForumTopicsUiState> =
        with(TopicsPreviewData) { sequenceOf(content, managed, loadingMore, loading, empty, error) }
}
