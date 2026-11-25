package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.AppInfo
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachPreview
import dev.kyralabs.pingogram.feature.chat.ui.attach.attachOptions
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.composerState
import dev.kyralabs.pingogram.feature.chat.ui.thread.composer
import dev.kyralabs.pingogram.feature.chat.ui.thread.header
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Maps what the conversation is made from (the chat, its messages, its people, the time and what shows over the
 * messages) into one [ChatUiState]: the header, the items, the composer and the sheets. [ChatViewModel] combines the
 * sources and calls [state] on its dispatcher; the time formatter is kept while the zone, language and clock setting
 * stay, for [state] and for the view model's date sheet and scheduled bar.
 */
internal class ChatStateMapper(
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    private val locale: () -> Locale,
    private val appInfo: AppInfo,
    /** Whether the conversation is a forum topic's or a message thread's, whose shortcuts differ. */
    private val inThread: Boolean,
) {
    private val mapper = ChatItemMapper()
    private var timeFormatter: ChatTimeFormatter? = null

    /** Maps the header, the items, the composer and what shows over them. */
    fun state(
        chatSources: ChatSources,
        messages: MessageSources,
        people: ChatPeople,
        now: Instant,
        overlays: Overlays,
    ): ChatUiState {
        val lookup = chatSources.chat
        val chat = (lookup as? ChatLookup.Found)?.chat
        val unreadAfterId = chatSources.unreadAfterId
        if (chat == null || unreadAfterId == null) {
            return ChatUiState(content = (lookup as? ChatLookup.Failed)?.error.toContent())
        }
        val formatter = timeFormatter()
        val group = chatSources.group
        val header =
            HeaderSources(
                chat = chat,
                memberCount = group.memberCount,
                secretChat = group.secretChat,
                actions = chatSources.actions,
                people = people,
                now = now,
                timeFormatter = formatter,
                isForum = group.supergroup?.isForum == true,
                recentSenders = if (chat.isGroup) recentSenders(messages.state.history.messages) else emptyList(),
            ).toHeader()
                .let { chatSources.thread.header(it, chat) }
        val history = messages.state.history
        val user = chat.userId?.let(people.users::get)
        val composer =
            composerState(chat, user, group.basicGroup, group.supergroup, group.secretChat)
                .let { chatSources.thread.composer(it, group.supergroup) }
        val canWrite = composer is ComposerUiState.Available
        val premium = people.myUserId?.let(people.users::get)?.isPremium == true
        val options = attachOptions(chat, user, people.myUserId, isPremium = premium)
        val canSchedule = (composer as? ComposerUiState.Available)?.canSchedule == true
        val bar = overlays.bar(chat, history, people)
        return ChatUiState(
            header = header,
            content = mapper.content(chat, messages, people, unreadAfterId, now, formatter),
            unreadCount = chat.unreadCount,
            unreadMentionCount = chat.unreadMentionCount,
            showsUpdateLink = !appInfo.isFromAppStore,
            groupMemberCount = group.memberCount.takeIf { chat.isGroup },
            typing = if (chat.isGroup) typingBubble(chatSources.actions, people, header) else null,
            composer = if (canWrite) composer.withBar(bar, options, premium) else composer,
            // A message deleted meanwhile takes its sheet with it.
            messageActions = overlays.sheet?.takeIf { history.messages.findById(it.messageId) != null },
            deleteDialog = overlays.deleteDialog,
            shortcuts = chat.shortcuts(canWrite, inThread = inThread),
            // What was picked or written stays while the user may not write; it shows again when they may.
            attachPreview =
                overlays.preview?.takeIf { canWrite }?.let { preview ->
                    AttachPreview(
                        preview.kind,
                        preview.items,
                        canSchedule,
                        preview.offersOriginal,
                        preview.original,
                        isReplacement = preview.replacing != null,
                    )
                },
            poll = overlays.pollSheet(chat, canWrite, canSchedule, options),
            checklistForm = overlays.checklistSheet(canWrite, canSchedule, options),
        )
    }

    /** The formatter for the current time zone, language and clock setting, which can change while the app runs. */
    fun timeFormatter(): ChatTimeFormatter {
        val zone: ZoneId = clock.zone()
        val locale = locale()
        val is24HourClock = timePatterns.is24HourClock()
        return timeFormatter?.takeIf { it.isFor(zone, locale, is24HourClock) }
            ?: ChatTimeFormatter(zone, locale, is24HourClock, timePatterns).also { timeFormatter = it }
    }
}

/** The composer with [bar], and the [attachOptions] of the chat unless it edits: an edit sends no attachments. */
private fun ComposerUiState.Available.withBar(
    bar: ComposerBar?,
    attachOptions: ImmutableList<AttachOption>,
    canRepeat: Boolean,
): ComposerUiState.Available =
    copy(
        bar = bar,
        attachOptions = if (bar is ComposerBar.Edit) persistentListOf() else attachOptions,
        // A Telegram Premium subscriber may repeat what they schedule; an edit schedules nothing.
        canRepeat = canRepeat && canSchedule && bar !is ComposerBar.Edit,
    )

/** Not found for a chat TDLib does not know, an error with a retry for other failures; loading without a failure. */
private fun DomainError?.toContent(): ChatContent =
    when (this) {
        null -> ChatContent.Loading
        DomainError.NotFound -> ChatContent.NotFound
        else -> ChatContent.Error(this)
    }
