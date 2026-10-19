package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.BasicGroup
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.ui.text.PREVIEW_MAX_LENGTH
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.contentKindText
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.collections.immutable.ImmutableMap
import java.text.NumberFormat
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

// How SearchViewModel maps chats and found messages to rows, reusing the chat list's mapping (ChatListItemMapper.kt)
// for names, placeholders, badges and online dots.

/**
 * What rows show besides their chats and messages, as of one emission: the users and groups they name, the current
 * user, the minute (for online dots and dates), and how to format counts and dates.
 */
internal class SearchRowSources(
    val users: ImmutableMap<Long, User>,
    val supergroups: ImmutableMap<Long, Supergroup>,
    val basicGroups: ImmutableMap<Long, BasicGroup>,
    val myUserId: Long?,
    val now: Instant,
    val timeFormatter: ChatListTimeFormatter,
) {
    /** Counts in the language of [timeFormatter]. */
    val numbers: NumberFormat = NumberFormat.getIntegerInstance(timeFormatter.locale)
}

/** The row of this chat in search: as in the chat list, with the username or the member count below the name. */
internal fun Chat.toSearchItem(sources: SearchRowSources): SearchChatItem {
    val user = userId?.let(sources.users::get)
    val supergroup = (type as? ChatType.Supergroup)?.let { sources.supergroups[it.supergroupId] }
    val isSavedMessages = type is ChatType.Private && userId == sources.myUserId
    val isDeletedAccount = user?.isDeleted == true
    return SearchChatItem(
        id = id,
        title = chatTitle(this, isSavedMessages, isDeletedAccount),
        photo = if (isSavedMessages) null else photo,
        placeholderIcon = placeholderIcon(isSavedMessages, isDeletedAccount),
        isOnline = !isSavedMessages && user?.showsOnlineAt(sources.now) == true,
        badge = titleBadge(user, supergroup),
        isSecret = type is ChatType.Secret,
        isMuted = isMuted,
        subtitle = if (isSavedMessages || isDeletedAccount) null else subtitle(user, supergroup, sources),
    )
}

/** "@ada", else "1,204 members" or "12,301 subscribers"; null for neither. */
private fun Chat.subtitle(
    user: User?,
    supergroup: Supergroup?,
    sources: SearchRowSources,
): UiText? {
    val username = user?.usernames?.firstOrNull() ?: supergroup?.usernames?.firstOrNull()
    val basicGroup = (type as? ChatType.BasicGroup)?.let { sources.basicGroups[it.basicGroupId] }
    val members = supergroup?.memberCount ?: basicGroup?.memberCount ?: 0
    val plural = if (supergroup?.isChannel == true) R.plurals.search_subscribers else R.plurals.search_members
    return when {
        username != null -> UiText.resource(R.string.search_username, username)
        members > 0 -> UiText.plural(plural, members, sources.numbers.format(members))
        else -> null
    }
}

/**
 * The row of this message in search: its chat's avatar and name, the sender in a group, its text from shortly before
 * the first match of [highlight], and the date.
 */
internal fun FoundMessage.toSearchItem(
    sources: SearchRowSources,
    highlight: MatchHighlight,
): SearchMessageItem {
    val user = chat.userId?.let(sources.users::get)
    val supergroup = (chat.type as? ChatType.Supergroup)?.let { sources.supergroups[it.supergroupId] }
    val isSavedMessages = chat.type is ChatType.Private && chat.userId == sources.myUserId
    val isDeletedAccount = user?.isDeleted == true
    return SearchMessageItem(
        chatId = chat.id,
        messageId = message.id,
        chatTitle = chatTitle(chat, isSavedMessages, isDeletedAccount),
        photo = if (isSavedMessages) null else chat.photo,
        placeholderIcon = placeholderIcon(isSavedMessages, isDeletedAccount),
        badge = titleBadge(user, supergroup),
        sender = sender(sources),
        preview = preview(sources, highlight),
        date = sources.timeFormatter.format(message.date, sources.now),
    )
}

/**
 * "You" or the sender's first name in a group, as the chat list's previews show it; none in other chats, for channel
 * posts, for messages the group sends as itself, for unknown users and for service messages.
 */
private fun FoundMessage.sender(sources: SearchRowSources): UiText? {
    val sender = message.sender
    return when {
        !chat.isGroup || message.isChannelPost || message.content is MessageContent.Service -> null
        message.isOutgoing -> UiText.resource(UiR.string.preview_sender_you)
        sender is MessageSender.User -> sources.users[sender.userId]?.shortName()
        else -> null
    }
}

/**
 * The message's text, or its caption after the kind of media ("Photo · …"), from shortly before the first match
 * ([matchSnippet]); the chat list's preview for anything else (a file's name, a poll's question).
 */
private fun FoundMessage.preview(
    sources: SearchRowSources,
    highlight: MatchHighlight,
): UiText {
    val content = message.content
    val caption = content.captionText?.text.orEmpty()
    return when {
        content is MessageContent.Text -> {
            UiText.Plain(matchSnippet(content.text.text, highlight))
        }

        caption.isNotBlank() -> {
            UiText.resource(
                UiR.string.preview_with_text,
                contentKindText(content),
                UiText.Plain(matchSnippet(caption, highlight)),
            )
        }

        else -> {
            messagePreviewText(message, sources.myUserId) { sender ->
                when (sender) {
                    is MessageSender.User -> sources.users[sender.userId]?.shortName()
                    is MessageSender.Chat -> if (sender.chatId == chat.id) UiText.Plain(chat.title) else null
                }
            }
        }
    }
}

/** The caption of media that has one; null for other content. */
private val MessageContent.captionText: FormattedText?
    get() =
        when (this) {
            is MessageContent.Photo -> caption
            is MessageContent.Video -> caption
            is MessageContent.Animation -> caption
            is MessageContent.VoiceNote -> caption
            is MessageContent.Audio -> caption
            is MessageContent.Document -> caption
            else -> null
        }

/**
 * [text] as one line for a row, as the chat list's previews are (at most [PREVIEW_MAX_LENGTH] characters, line breaks
 * as spaces), but starting at a word shortly before the first match of [highlight], after "…", when the match is too
 * far in to show on two lines otherwise.
 */
internal fun matchSnippet(
    text: String,
    highlight: MatchHighlight,
): String {
    val match = highlight.firstMatch(text) ?: 0
    var start = 0
    if (match > SNIPPET_LEAD) {
        start = match - SNIPPET_LEAD
        // At the start of a word, if one starts before the match.
        val space = text.indexOf(' ', start)
        if (space in start until match) start = space + 1
        if (Character.isLowSurrogate(text[start])) start++
    }
    var end = minOf(text.length, start + PREVIEW_MAX_LENGTH)
    if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
    val line = LINE_BREAKS.replace(text.substring(start, end), " ").trim()
    return if (start > 0) "$ELLIPSIS$line" else line
}

/** How many characters before the first match a snippet starts at most: about half a line on a phone. */
private const val SNIPPET_LEAD = 24

private const val ELLIPSIS = "…"

private val LINE_BREAKS = Regex("""\s*[\r\n]+\s*""")
