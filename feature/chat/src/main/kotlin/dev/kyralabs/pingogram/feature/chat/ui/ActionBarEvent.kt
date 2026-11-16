package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.actionbar.ChatActionBarViewModel

/**
 * What the user does with a chat's action bar (`docs/DESIGN_SYSTEM.md` §6 "Chat action bar"), handled by
 * [ChatActionBarViewModel] rather than the conversation's view model: the route hands these events to it. "Report" is
 * the conversation's own [ChatEvent.ReportChat].
 */
sealed interface ActionBarEvent : ChatEvent {
    /** "Block": the question before blocking opens. */
    data object Block : ActionBarEvent

    /** The question's "Block": the other user is blocked and the bar dismissed. */
    data object ConfirmBlock : ActionBarEvent

    /** The question closes without blocking. */
    data object DismissBlock : ActionBarEvent

    /** "Add contact": the other user joins the contacts, by the name they have. */
    data object AddContact : ActionBarEvent

    /** "Share my phone number" with the other user, a contact. */
    data object SharePhoneNumber : ActionBarEvent

    /** The bar's close button: TDLib forgets the bar for this chat. */
    data object Dismiss : ActionBarEvent
}
