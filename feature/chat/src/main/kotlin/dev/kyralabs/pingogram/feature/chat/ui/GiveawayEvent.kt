package dev.kyralabs.pingogram.feature.chat.ui

/** The events of giveaways: the details sheet ("Learn more") of a giveaway or of its winners message. */
sealed interface GiveawayEvent : ChatEvent {
    /** "Learn more" on the giveaway of message [messageId]: open its details, asking TDLib what it knows of it. */
    data class OpenInfo(
        val messageId: Long,
    ) : GiveawayEvent

    /** Ask again after the details failed to load. */
    data object RetryInfo : GiveawayEvent

    data object DismissInfo : GiveawayEvent
}
