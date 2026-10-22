package dev.kyralabs.pingogram.feature.profile.chatinfo

import dev.kyralabs.pingogram.core.domain.model.MemberFilter

/**
 * Where the info page of a group or channel leads, as features do not know each other: back ([onBack]); the chat
 * gone after leaving or deleting it ([onLeft]); a chat or profile a mention or member names ([onOpenChat],
 * [onOpenProfile]); the members page ([onOpenMembers]) and the administrators' pages ([onOpenPage]); the shared
 * media's photos, videos and GIFs in the media viewer ([onOpenMedia]), its files at their message in the chat
 * ([onOpenMessage]).
 */
@Suppress("LongParameterList") // One callback per place the page leads to.
internal class ChatInfoNavigation(
    val onBack: () -> Unit,
    val onLeft: () -> Unit,
    val onOpenChat: (chatId: Long) -> Unit,
    val onOpenProfile: (userId: Long) -> Unit,
    val onOpenMembers: (filter: MemberFilter) -> Unit,
    val onOpenPage: (page: ChatInfoPage) -> Unit,
    val onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    val onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
) {
    /** Follows [effect] if it leads somewhere; false for the effects the page shows itself. */
    fun navigate(effect: ChatInfoEffect): Boolean {
        when (effect) {
            is ChatInfoEffect.OpenChat -> onOpenChat(effect.chatId)
            is ChatInfoEffect.OpenProfile -> onOpenProfile(effect.userId)
            is ChatInfoEffect.OpenMembers -> onOpenMembers(effect.filter)
            is ChatInfoEffect.Open -> onOpenPage(effect.page)
            ChatInfoEffect.Left -> onLeft()
            else -> return false
        }
        return true
    }
}
