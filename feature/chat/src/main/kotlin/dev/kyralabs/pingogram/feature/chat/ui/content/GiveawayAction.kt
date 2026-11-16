package dev.kyralabs.pingogram.feature.chat.ui.content

/** What a tap in a giveaway's bubble asks for; the bubble turns it into the event of its message. */
internal sealed interface GiveawayAction {
    /** "Learn more": the details sheet. */
    data object LearnMore : GiveawayAction

    /** A participating channel's chip. */
    data class OpenChat(
        val chatId: Long,
    ) : GiveawayAction

    /** A winner's tile. */
    data class OpenWinner(
        val userId: Long,
    ) : GiveawayAction
}
