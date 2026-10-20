package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.collections.immutable.ImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The contacts the New chat screen lists below its actions (`REQUIREMENTS.md` FR-L7); the actions themselves
 * ([NewChatAction]) are always there.
 */
@Immutable
sealed interface NewChatUiState {
    /** The contacts are on their way. */
    data object Loading : NewChatUiState

    /** The user has no contacts. */
    data object Empty : NewChatUiState

    /** Reading the contacts failed with [error]: shown with a retry ([NewChatEvent.Retry]). */
    data class Error(
        val error: DomainError,
    ) : NewChatUiState

    /** The contacts, sorted by name, each as its private chat, mapped as search maps them. */
    data class Content(
        val contacts: ImmutableList<SearchChatItem>,
    ) : NewChatUiState {
        override fun toString() = "Content(${contacts.size})"
    }
}

/**
 * What the New chat screen offers above the contacts, each with its Material Symbol and label. Their screens come with
 * groups, channels and contacts (`docs/ROADMAP.md` M4); until then they say so. The symbols are the closest of those
 * bundled ("chat", "notifications", "contacts") until "group_add", "campaign" and "person_add" are.
 */
enum class NewChatAction(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
) {
    Group(UiR.drawable.ic_chat, R.string.new_chat_group),
    Channel(UiR.drawable.ic_notifications, R.string.new_chat_channel),
    Contact(UiR.drawable.ic_contacts, R.string.new_chat_contact),
}

/** What the user does on the New chat screen, sent to [NewChatViewModel.onEvent]. */
sealed interface NewChatEvent {
    /** One of the actions above the contacts was tapped. */
    data class Action(
        val action: NewChatAction,
    ) : NewChatEvent

    /** A contact was tapped: opens the private chat [chatId]. */
    data class OpenChat(
        val chatId: Long,
    ) : NewChatEvent

    /** "Try again" after reading the contacts failed. */
    data object Retry : NewChatEvent
}

/** One-off effects of [NewChatViewModel], handled by the screen once each. */
sealed interface NewChatEffect {
    /** Opens the conversation of chat [chatId]. */
    data class OpenChat(
        val chatId: Long,
    ) : NewChatEffect

    /** An action whose screen a later version adds: a snackbar says so. */
    data object NotAvailableYet : NewChatEffect
}
