package dev.kyralabs.pingogram.feature.profile.chatinfo

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chart
import dev.kyralabs.pingogram.core.domain.model.ChartSeries
import dev.kyralabs.pingogram.core.domain.model.ChartStyle
import dev.kyralabs.pingogram.core.domain.model.ChatStatistics
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.domain.model.StatisticGraph
import dev.kyralabs.pingogram.core.domain.repository.ChatStatisticsRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.GraphState
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.PollStatisticsUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.PollStatisticsViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** A poll's statistics page on a fake [ChatStatisticsRepository] and a message read by a lambda. */
@OptIn(ExperimentalCoroutinesApi::class)
class PollStatisticsViewModelTest : GroupViewModelTest() {
    private val chart =
        Chart(
            x = persistentListOf(DAY_1, DAY_2),
            series =
                persistentListOf(
                    ChartSeries("Summer", persistentListOf(40, 62)),
                    ChartSeries("Winter", persistentListOf(10, 16)),
                ),
            style = ChartStyle.Bar,
            stacked = true,
        )

    private class FakeStatistics(
        var votes: Outcome<StatisticGraph>,
    ) : ChatStatisticsRepository {
        val graph = CompletableDeferred<Outcome<StatisticGraph>>()
        val asked = mutableListOf<Pair<Long, Long>>()

        override suspend fun statistics(chatId: Long): Outcome<ChatStatistics> = Outcome.Failure(DomainError.NotFound)

        override suspend fun graph(
            chatId: Long,
            token: String,
        ): Outcome<StatisticGraph> = graph.await()

        override suspend fun pollVotes(
            chatId: Long,
            messageId: Long,
        ): Outcome<StatisticGraph> {
            asked += chatId to messageId
            return votes
        }
    }

    private fun text(value: String) = FormattedText(value, persistentListOf())

    private val poll =
        Poll(
            id = 1,
            question = text("Which season?"),
            options =
                persistentListOf(
                    PollOption(0, text("Summer"), 102, 72, isChosen = false, isCorrect = false),
                    PollOption(1, text("Winter"), 40, 28, isChosen = false, isCorrect = false),
                ),
            totalVoterCount = 142,
            isAnonymous = false,
            allowsMultipleAnswers = false,
            canSeeResults = true,
            isClosed = false,
            type = PollType.Regular,
        )

    private fun message(content: MessageContent) =
        Message(
            id = MESSAGE,
            chatId = CHAT,
            sender = MessageSender.Chat(CHAT),
            date = 0,
            editDate = 0,
            isOutgoing = false,
            sendingState = null,
            isPinned = false,
            isChannelPost = true,
            containsUnreadMention = false,
            mediaAlbumId = 0,
            authorSignature = "",
            viaBotUserId = 0,
            replyTo = null,
            forwardInfo = null,
            interactionInfo = null,
            selfDestruct = null,
            content = content,
        )

    private var answer: Outcome<Message> = Outcome.Success(message(MessageContent.Poll(poll, text(""))))

    private fun TestScope.viewModel(statistics: FakeStatistics): PollStatisticsViewModel =
        PollStatisticsViewModel(CHAT, MESSAGE, statistics, { _, _ -> answer }, clock, patterns) { Locale.US }.also {
            created += it
            runCurrent()
        }

    private val PollStatisticsViewModel.content get() = uiState.value as PollStatisticsUiState.Content

    @Test
    fun `shows the poll's totals and its votes over time`() =
        runTest {
            val statistics = FakeStatistics(Outcome.Success(StatisticGraph.Loaded(chart)))
            val content = viewModel(statistics).content

            assertEquals("Which season?", content.question)
            assertEquals(UiText.plural(R.plurals.poll_statistics_votes, 142, 142), content.votes)
            assertEquals(listOf("Summer" to "72%", "Winter" to "28%"), content.options.map { it.text to it.percent })
            val drawn = content.graph as GraphState.Drawn
            assertEquals(78L, drawn.chart.maxValue)
            assertEquals(listOf(CHAT to MESSAGE), statistics.asked)
        }

    @Test
    fun `a graph Telegram gives as a token is read by it`() =
        runTest {
            val statistics = FakeStatistics(Outcome.Success(StatisticGraph.Pending("votes")))
            val viewModel = viewModel(statistics)
            assertEquals(GraphState.Loading, viewModel.content.graph)

            statistics.graph.complete(Outcome.Success(StatisticGraph.Loaded(chart)))
            runCurrent()

            assertTrue(viewModel.content.graph is GraphState.Drawn)
        }

    @Test
    fun `a poll without a graph yet keeps its totals`() =
        runTest {
            val content =
                viewModel(
                    FakeStatistics(Outcome.Failure(DomainError.Unknown(400, "STATS_NOT_READY"))),
                ).content

            assertEquals(GraphState.Empty, content.graph)
            assertEquals(2, content.options.size)
        }

    @Test
    fun `a message that cannot be read shows the error, and trying again reads it`() =
        runTest {
            answer = Outcome.Failure(DomainError.Network)
            val viewModel = viewModel(FakeStatistics(Outcome.Success(StatisticGraph.Loaded(chart))))
            assertEquals(PollStatisticsUiState.Error(DomainError.Network), viewModel.uiState.value)

            answer = Outcome.Success(message(MessageContent.Poll(poll, text(""))))
            viewModel.retry()
            runCurrent()

            assertEquals("Which season?", viewModel.content.question)
        }

    @Test
    fun `a message without a poll is not found`() =
        runTest {
            answer = Outcome.Success(message(MessageContent.Text(text("Hi"), null)))

            assertEquals(
                PollStatisticsUiState.Error(DomainError.NotFound),
                viewModel(FakeStatistics(Outcome.Success(StatisticGraph.Loaded(chart)))).uiState.value,
            )
        }

    private companion object {
        const val MESSAGE = 77L
        const val DAY_1 = 1_792_540_800_000L
        const val DAY_2 = DAY_1 + 86_400_000L
    }
}
