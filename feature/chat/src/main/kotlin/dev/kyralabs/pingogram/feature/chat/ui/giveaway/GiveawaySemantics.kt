package dev.kyralabs.pingogram.feature.chat.ui.giveaway

import android.content.res.Resources
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.GiveawayEvent

/**
 * What screen readers hear of [bubble]'s giveaway within its bubble's description (`docs/DESIGN_SYSTEM.md` §9): the
 * prize, the participating channels, when the winners are picked and who takes part; or the winners message's title,
 * who won what, and the winners' names. Empty for other content.
 */
internal fun giveawayDescription(
    resources: Resources,
    bubble: ChatItem.Bubble,
): String {
    val locale = resources.configuration.locales[0]
    val ui = bubble.giveaway ?: GiveawayUi()
    return when (val content = bubble.content) {
        is MessageContent.Giveaway -> {
            val parameters = content.parameters
            val who = if (parameters.onlyNewMembers) R.plurals.giveaway_who_new else R.plurals.giveaway_who_all
            listOfNotNull(
                resources.getString(R.string.giveaway_prize_kicker),
                resources.getString(R.string.giveaway_prize_times, content.winnerCount) + " " +
                    resources.prizeText(content.prize, locale),
                ui.channels.takeIf { it.isNotEmpty() }?.let { channels ->
                    resources.getString(R.string.giveaway_channels) + ": " +
                        channels.joinToString { it.name.resolve(resources) }
                },
                resources.getString(R.string.giveaway_winners_date) + " " +
                    giveawayDateText(parameters.winnersSelectionDate, locale),
                resources.getQuantityString(who, parameters.chatIds.size),
                parameters.countryCodes.takeIf { it.isNotEmpty() }?.let {
                    resources.getString(R.string.giveaway_countries, countriesText(it, locale))
                },
                parameters.prizeDescription.takeIf { it.isNotBlank() }?.let {
                    resources.getString(R.string.giveaway_also, it)
                },
            ).joinToString(", ")
        }

        is MessageContent.GiveawayWinners -> {
            listOfNotNull(
                resources.getString(R.string.giveaway_winners_title),
                resources.winnersWonText(content.winnerCount, content.prize, locale),
                when {
                    content.wasRefunded -> resources.getString(R.string.giveaway_refunded)
                    ui.winners.isEmpty() -> resources.getString(R.string.giveaway_winners_private)
                    else -> ui.winners.joinToString { it.name.resolve(resources) }
                },
                content.unclaimedPrizeCount.takeIf { it > 0 }?.let {
                    resources.getQuantityString(R.plurals.giveaway_unclaimed, it, it)
                },
            ).joinToString(", ")
        }

        else -> {
            ""
        }
    }
}

/**
 * The custom actions of [bubble]'s giveaway, labels and events: "Learn more", then "Open <channel>" for each
 * participating channel and "Open the profile of <winner>" for each winner. None for other content.
 */
internal fun giveawayActions(
    resources: Resources,
    bubble: ChatItem.Bubble,
): List<Pair<String, ChatEvent>> {
    val content = bubble.content
    if (content !is MessageContent.Giveaway && content !is MessageContent.GiveawayWinners) return emptyList()
    val ui = bubble.giveaway ?: GiveawayUi()
    return listOf(resources.getString(R.string.giveaway_learn_more) to GiveawayEvent.OpenInfo(bubble.id)) +
        ui.channels.map { channel ->
            resources.getString(R.string.giveaway_open_channel, channel.name.resolve(resources)) to
                ChatEvent.OpenChat(channel.id)
        } +
        ui.winners.map { winner ->
            resources.getString(R.string.giveaway_open_winner, winner.name.resolve(resources)) to
                ChatEvent.OpenProfile(winner.id)
        }
}
