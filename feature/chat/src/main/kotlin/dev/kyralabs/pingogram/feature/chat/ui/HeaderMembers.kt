package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SenderAction
import dev.kyralabs.pingogram.core.ui.text.fullName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// The people of a group's cluster in the experimental top bar (`docs/DESIGN_SYSTEM.md` §13.5): who wrote last among
// the messages loaded, asking TDLib for nothing.

/**
 * Up to [HEADER_MEMBERS] people of [HeaderSources.recentSenders] TDLib has announced, newest first; none outside
 * groups.
 */
internal fun HeaderSources.members(): ImmutableList<HeaderMember> =
    if (!chat.isGroup) {
        persistentListOf()
    } else {
        recentSenders
            .asSequence()
            .mapNotNull(people.users::get)
            .take(HEADER_MEMBERS)
            .map { HeaderMember(it.id, it.fullName(), it.profilePhoto) }
            .toImmutableList()
    }

/** The most people the experimental top bar's cluster shows (`docs/DESIGN_SYSTEM.md` §13.5). */
private const val HEADER_MEMBERS = 5

/** The users who sent [messages] (newest first, as `ChatHistory` holds them), each once, at most [limit]. */
internal fun recentSenders(
    messages: List<Message>,
    limit: Int = RECENT_SENDERS_SCANNED,
): List<Long> =
    messages
        .asSequence()
        .mapNotNull { (it.sender as? MessageSender.User)?.userId }
        .distinct()
        .take(limit)
        .toList()

/** How many senders the top bar looks for, so a few TDLib has not announced still leave five. */
private const val RECENT_SENDERS_SCANNED = 8

/**
 * The first user doing something in a group (`SenderAction`), as the experimental look's typing bubble shows them,
 * with what [header]'s subtitle says; null while nobody is, or TDLib has not announced who.
 */
internal fun typingBubble(
    actions: List<SenderAction>,
    people: ChatPeople,
    header: ChatHeader,
): TypingBubble? {
    val text = (header.subtitle as? ChatSubtitle.Action)?.text ?: return null
    val user = actions.firstNotNullOfOrNull { (it.sender as? MessageSender.User)?.userId?.let(people.users::get) }
    return user?.let {
        TypingBubble(it.id, it.fullName(), it.profilePhoto, senderHueOf(it, it.id), text)
    }
}
