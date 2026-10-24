package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.mapper.chartOf
import dev.kyralabs.pingogram.core.domain.model.ChartStyle
import dev.kyralabs.pingogram.core.domain.model.StatisticGraph
import dev.kyralabs.pingogram.core.domain.model.StatisticGraphKind
import dev.kyralabs.pingogram.core.domain.model.StatisticKind
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [TdlibChatStatisticsRepository] and Telegram's chart JSON on a [FakeTdlibClient], on Robolectric for the platform's
 * `org.json`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TdlibChatStatisticsRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatStatisticsRepository(client)

    private fun value(
        value: Double,
        previous: Double = 0.0,
    ) = TdApi.StatisticalValue(value, previous, if (previous == 0.0) 0.0 else (value - previous) / previous * 100)

    private fun channel() =
        TdApi.ChatStatisticsChannel().also {
            it.period = TdApi.DateRange(1_000, 2_000)
            it.memberCount = value(1_200.0, 1_000.0)
            it.meanMessageViewCount = value(300.0, 250.0)
            it.meanMessageShareCount = value(0.0)
            it.meanMessageReactionCount = value(12.0, 10.0)
            it.meanStoryViewCount = value(0.0)
            it.meanStoryShareCount = value(0.0)
            it.meanStoryReactionCount = value(0.0)
            it.enabledNotificationsPercentage = 42.5
            it.memberCountGraph = TdApi.StatisticalGraphData(LINES, "")
            it.joinGraph = TdApi.StatisticalGraphAsync("joins-token")
            it.muteGraph = TdApi.StatisticalGraphError("Not enough data")
        }

    @Test
    fun `parses lines, with the series' names, and their x values`() {
        val chart = chartOf(LINES)!!

        assertEquals(listOf(1L, 2L, 3L), chart.x)
        assertEquals(listOf("Joined", "Left"), chart.series.map { it.name })
        assertEquals(listOf(5L, 7L, 9L), chart.series[0].values)
        assertEquals(ChartStyle.Line, chart.style)
        assertFalse(chart.stacked)
    }

    @Test
    fun `parses stacked bars and shares of the whole`() {
        val bars =
            chartOf(LINES.replace("\"line\"", "\"bar\"").replace("{\"columns\"", "{\"stacked\":true,\"columns\""))
        val area =
            chartOf(LINES.replace("\"line\"", "\"area\"").replace("{\"columns\"", "{\"percentage\":true,\"columns\""))

        assertEquals(ChartStyle.Bar, bars?.style)
        assertTrue(bars!!.stacked)
        assertEquals(ChartStyle.Area, area?.style)
        assertTrue(area!!.stacked && area.percentage)
    }

    @Test
    fun `data that does not parse, has no x or uneven series is no chart`() {
        assertNull(chartOf("not json"))
        assertNull(chartOf("""{"columns":[["y0",1,2]],"types":{"y0":"line"}}"""))
        assertNull(chartOf("""{"columns":[["x",1,2],["y0",1]],"types":{"x":"x","y0":"line"}}"""))
    }

    @Test
    fun `maps a channel's values and graphs, leaving out values without data`() =
        runTest {
            client.on(TdApi.GetChatStatistics::class, TdResult.Success(channel()))

            val statistics = (repository.statistics(CHAT) as Outcome.Success).value

            assertTrue(statistics.isChannel)
            assertEquals(1_000L to 2_000L, statistics.periodStart to statistics.periodEnd)
            assertEquals(
                listOf(
                    StatisticKind.Members,
                    StatisticKind.ViewsPerPost,
                    StatisticKind.ReactionsPerPost,
                    StatisticKind.EnabledNotifications,
                ),
                statistics.values.map { it.kind },
            )
            assertEquals(20.0, statistics.values.first().growthPercent, 0.001)
            assertEquals(
                listOf(StatisticGraphKind.Members, StatisticGraphKind.Joins, StatisticGraphKind.Muted),
                statistics.graphs.map { it.kind },
            )
            assertTrue(statistics.graphs[0].graph is StatisticGraph.Loaded)
            assertEquals(StatisticGraph.Pending("joins-token"), statistics.graphs[1].graph)
            assertFalse(client.sentOf<TdApi.GetChatStatistics>().single().isDark)
        }

    @Test
    fun `a supergroup's top senders come with its statistics`() =
        runTest {
            val supergroup =
                TdApi.ChatStatisticsSupergroup().also {
                    it.period = TdApi.DateRange(1_000, 2_000)
                    it.memberCount = value(50.0, 40.0)
                    it.messageCount = value(900.0, 800.0)
                    it.viewerCount = value(30.0, 25.0)
                    it.senderCount = value(12.0, 10.0)
                    it.topSenders = arrayOf(TdApi.ChatStatisticsMessageSenderInfo(7, 120, 40))
                }
            client.on(TdApi.GetChatStatistics::class, TdResult.Success(supergroup))

            val statistics = (repository.statistics(CHAT) as Outcome.Success).value

            assertFalse(statistics.isChannel)
            assertEquals(7L to 120, statistics.topSenders.single().let { it.userId to it.messageCount })
        }

    @Test
    fun `loads a graph by its token, and a refusal is a domain error`() =
        runTest {
            client.on(TdApi.GetStatisticalGraph::class, TdResult.Success(TdApi.StatisticalGraphData(LINES, "")))

            val graph = repository.graph(CHAT, "joins-token")

            assertTrue((graph as Outcome.Success).value is StatisticGraph.Loaded)
            assertEquals("joins-token", client.sentOf<TdApi.GetStatisticalGraph>().single().token)

            client.on(TdApi.GetChatStatistics::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            assertEquals(Outcome.Failure(DomainError.AdminRightsRequired), repository.statistics(CHAT))
        }

    private companion object {
        const val CHAT = -100L
        const val LINES =
            """{"columns":[["x",1,2,3],["y0",5,7,9],["y1",1,2,1]],""" +
                """"types":{"x":"x","y0":"line","y1":"line"},"names":{"y0":"Joined","y1":"Left"}}"""
    }
}
