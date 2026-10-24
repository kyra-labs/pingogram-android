package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatStatistics
import dev.kyralabs.pingogram.core.domain.model.StatisticGraph
import dev.kyralabs.pingogram.core.domain.model.StatisticGraphEntry
import dev.kyralabs.pingogram.core.domain.model.StatisticGraphKind
import dev.kyralabs.pingogram.core.domain.model.StatisticKind
import dev.kyralabs.pingogram.core.domain.model.StatisticValue
import dev.kyralabs.pingogram.core.domain.model.TopSender
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/** The domain statistics of TDLib's; a kind of statistics this version does not know has no values or graphs. */
internal fun TdApi.ChatStatistics.toChatStatistics(): ChatStatistics =
    when (this) {
        is TdApi.ChatStatisticsSupergroup -> supergroup()
        is TdApi.ChatStatisticsChannel -> channel()
        else -> ChatStatistics(false, 0, 0, persistentListOf(), persistentListOf())
    }

private fun TdApi.ChatStatisticsSupergroup.supergroup() =
    ChatStatistics(
        isChannel = false,
        periodStart = period.startDate.toLong(),
        periodEnd = period.endDate.toLong(),
        values =
            listOfNotNull(
                memberCount.toValue(StatisticKind.Members),
                messageCount.toValue(StatisticKind.Messages),
                viewerCount.toValue(StatisticKind.Viewers),
                senderCount.toValue(StatisticKind.Senders),
            ).toImmutableList(),
        graphs =
            graphs(
                StatisticGraphKind.Members to memberCountGraph,
                StatisticGraphKind.Joins to joinGraph,
                StatisticGraphKind.JoinsBySource to joinBySourceGraph,
                StatisticGraphKind.Languages to languageGraph,
                StatisticGraphKind.MessageContent to messageContentGraph,
                StatisticGraphKind.Actions to actionGraph,
                StatisticGraphKind.ByHour to dayGraph,
                StatisticGraphKind.ByWeekday to weekGraph,
            ),
        topSenders =
            topSenders
                .orEmpty()
                .map { TopSender(it.userId, it.sentMessageCount, it.averageCharacterCount) }
                .toImmutableList(),
    )

private fun TdApi.ChatStatisticsChannel.channel() =
    ChatStatistics(
        isChannel = true,
        periodStart = period.startDate.toLong(),
        periodEnd = period.endDate.toLong(),
        values =
            listOfNotNull(
                memberCount.toValue(StatisticKind.Members),
                meanMessageViewCount.toValue(StatisticKind.ViewsPerPost),
                meanMessageShareCount.toValue(StatisticKind.SharesPerPost),
                meanMessageReactionCount.toValue(StatisticKind.ReactionsPerPost),
                meanStoryViewCount.toValue(StatisticKind.ViewsPerStory),
                meanStoryShareCount.toValue(StatisticKind.SharesPerStory),
                meanStoryReactionCount.toValue(StatisticKind.ReactionsPerStory),
                StatisticValue(StatisticKind.EnabledNotifications, enabledNotificationsPercentage, 0.0, 0.0),
            ).toImmutableList(),
        graphs =
            graphs(
                StatisticGraphKind.Members to memberCountGraph,
                StatisticGraphKind.Joins to joinGraph,
                StatisticGraphKind.Muted to muteGraph,
                StatisticGraphKind.ViewsByHour to viewCountByHourGraph,
                StatisticGraphKind.ViewsBySource to viewCountBySourceGraph,
                StatisticGraphKind.JoinsBySource to joinBySourceGraph,
                StatisticGraphKind.Languages to languageGraph,
                StatisticGraphKind.Interactions to messageInteractionGraph,
                StatisticGraphKind.Reactions to messageReactionGraph,
                StatisticGraphKind.StoryInteractions to storyInteractionGraph,
                StatisticGraphKind.StoryReactions to storyReactionGraph,
                StatisticGraphKind.InstantViewInteractions to instantViewInteractionGraph,
            ),
    )

/** A value; null where Telegram has none (no value in either period). */
private fun TdApi.StatisticalValue?.toValue(kind: StatisticKind): StatisticValue? =
    this?.takeUnless { it.value == 0.0 && it.previousValue == 0.0 }?.let {
        StatisticValue(kind, it.value, it.previousValue, it.growthRatePercentage)
    }

private fun graphs(vararg graphs: Pair<StatisticGraphKind, TdApi.StatisticalGraph?>) =
    graphs
        .mapNotNull { (kind, graph) -> graph?.toStatisticGraph()?.let { StatisticGraphEntry(kind, it) } }
        .toImmutableList()

/** A graph, or null for one without data or whose data does not parse. */
internal fun TdApi.StatisticalGraph.toStatisticGraph(): StatisticGraph? =
    when (this) {
        is TdApi.StatisticalGraphData -> chartOf(jsonData)?.let(StatisticGraph::Loaded)
        is TdApi.StatisticalGraphAsync -> StatisticGraph.Pending(token)
        is TdApi.StatisticalGraphError -> StatisticGraph.Failed(errorMessage)
        else -> null
    }
