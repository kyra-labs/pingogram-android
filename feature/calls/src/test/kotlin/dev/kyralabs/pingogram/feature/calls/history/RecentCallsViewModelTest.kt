package dev.kyralabs.pingogram.feature.calls.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.CallHistoryChange
import dev.kyralabs.pingogram.core.domain.model.CallHistoryEntry
import dev.kyralabs.pingogram.core.domain.model.CallHistoryPage
import dev.kyralabs.pingogram.core.domain.model.CallOutcome
import dev.kyralabs.pingogram.core.domain.repository.CallHistoryRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.feature.calls.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.calls.ui.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/** Recent calls: pages, days, names, the filter, calling back, deleting and the changes that arrive. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentCallsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCallHistoryRepository()
    private val users = FakeUserRepository()
    private val created = mutableListOf<RecentCallsViewModel>()

    init {
        Dispatchers.setMain(dispatcher)
        users.add(user(42, "Grace", "Hopper"))
        users.add(user(43, "Alan", "Turing"))
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        RecentCallsViewModel(savedState, repository, users, FixedClock, EnglishPatterns)
            .also {
                it.locale = { Locale.US }
                created += it
            }

    private fun RecentCallsViewModel.content() = uiState.value.content

    private fun RecentCallsViewModel.calls() =
        (content() as RecentCallsContent.Calls).items.filterIsInstance<RecentCallsItem.Call>()

    @Test
    fun `it shows loading, then the calls by day with their names, kinds, times and lengths`() =
        runTest(dispatcher) {
            repository.pages[""] =
                page(
                    entry(1, userId = 42, date = TODAY_NOON, outcome = CallOutcome.Missed),
                    entry(2, userId = 43, date = YESTERDAY_EVENING, isOutgoing = true, isVideo = true, duration = 245),
                )
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(RecentCallsContent.Loading, awaitItem().content)
                advanceUntilIdle()
                val items = (expectMostRecentItem().content as RecentCallsContent.Calls).items
                assertEquals(
                    listOf(
                        UiText.resource(R.string.recent_calls_today),
                        UiText.resource(R.string.recent_calls_yesterday),
                    ),
                    items.filterIsInstance<RecentCallsItem.Day>().map { it.label },
                )
                val (missed, outgoing) = items.filterIsInstance<RecentCallsItem.Call>()
                assertEquals(UiText.Plain("Grace Hopper"), missed.name)
                assertEquals(UiText.resource(UiR.string.preview_call_missed), missed.kind)
                assertEquals("12:00 PM", missed.time)
                assertNull(missed.duration)
                assertEquals(UiText.resource(UiR.string.preview_video_call_outgoing), outgoing.kind)
                assertEquals("4:05", outgoing.duration)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `no calls is the empty state, and a failed first page the error`() =
        runTest(dispatcher) {
            repository.pages[""] = page()
            val viewModel = viewModel()
            viewModel.uiState.test {
                advanceUntilIdle()
                assertEquals(RecentCallsContent.Empty, expectMostRecentItem().content)
                cancelAndIgnoreRemainingEvents()
            }

            repository.failure = DomainError.Network
            viewModel.onEvent(RecentCallsEvent.SelectFilter(CallFilter.Missed))
            viewModel.uiState.test {
                advanceUntilIdle()
                assertEquals(RecentCallsContent.Error(DomainError.Network), expectMostRecentItem().content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the missed filter searches only missed calls and is kept in the saved state`() =
        runTest(dispatcher) {
            val savedState = SavedStateHandle()
            val viewModel = viewModel(savedState)
            viewModel.uiState.test {
                advanceUntilIdle()
                viewModel.onEvent(RecentCallsEvent.SelectFilter(CallFilter.Missed))
                advanceUntilIdle()
                assertEquals(CallFilter.Missed, expectMostRecentItem().filter)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(listOf(false, true), repository.requests.map { it.second })
            val restored = viewModel(savedState)
            restored.uiState.test {
                advanceUntilIdle()
                assertEquals(CallFilter.Missed, expectMostRecentItem().filter)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(true, repository.requests.last().second)
        }

    @Test
    fun `the next page loads from where the last one ended, and a failed one can be tried again`() =
        runTest(dispatcher) {
            repository.pages[""] = page(entry(1, date = TODAY_NOON), next = "second")
            val viewModel = viewModel()
            viewModel.uiState.test {
                advanceUntilIdle()
                repository.failure = DomainError.Network
                viewModel.onEvent(RecentCallsEvent.LoadMore)
                advanceUntilIdle()
                assertEquals(
                    DomainError.Network,
                    (expectMostRecentItem().content as RecentCallsContent.Calls).pageError,
                )

                repository.failure = null
                repository.pages["second"] = page(entry(2, date = YESTERDAY_EVENING))
                viewModel.onEvent(RecentCallsEvent.Retry)
                advanceUntilIdle()
                val content = expectMostRecentItem().content as RecentCallsContent.Calls
                assertEquals(
                    listOf(1L, 2L),
                    content.items.filterIsInstance<RecentCallsItem.Call>().map { it.messageId },
                )
                assertEquals(true, content.reachedEnd)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("", "second", "second"), repository.requests.map { it.first })
        }

    @Test
    fun `a tap calls back the way the call went`() =
        runTest(dispatcher) {
            repository.pages[""] = page(entry(1, userId = 43, date = TODAY_NOON, isVideo = true))
            val viewModel = viewModel()
            viewModel.uiState.test {
                advanceUntilIdle()
                val call = viewModel.calls().single()
                viewModel.effect.test {
                    viewModel.onEvent(RecentCallsEvent.CallBack(call))
                    assertEquals(RecentCallsEffect.Call(43, isVideo = true), awaitItem())
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a tap on a conference call joins it while it goes on, and says when it is over`() =
        runTest(dispatcher) {
            repository.pages[""] =
                page(
                    entry(1, date = TODAY_NOON).copy(isGroupCall = true, isActive = true),
                    entry(2, date = TODAY_NOON - 60).copy(isGroupCall = true),
                )
            val viewModel = viewModel()
            viewModel.uiState.test {
                advanceUntilIdle()
                val (active, ended) = viewModel.calls()
                viewModel.effect.test {
                    viewModel.onEvent(RecentCallsEvent.CallBack(active))
                    assertEquals(RecentCallsEffect.JoinGroupCall(CHAT_ID, 1), awaitItem())
                    viewModel.onEvent(RecentCallsEvent.CallBack(ended))
                    assertEquals(RecentCallsEffect.GroupCallEnded, awaitItem())
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleting removes the call at once and puts it back when Telegram refuses`() =
        runTest(dispatcher) {
            repository.pages[""] = page(entry(1, date = TODAY_NOON), entry(2, date = TODAY_NOON - 60))
            val viewModel = viewModel()
            viewModel.uiState.test {
                advanceUntilIdle()
                viewModel.effect.test {
                    viewModel.onEvent(RecentCallsEvent.Delete(viewModel.calls().first()))
                    advanceUntilIdle()
                    assertEquals(RecentCallsEffect.Deleted, awaitItem())
                    assertEquals(listOf(2L), viewModel.calls().map { it.messageId })

                    repository.deleteFailure = DomainError.Unknown(400, "MESSAGE_DELETE_FORBIDDEN")
                    viewModel.onEvent(RecentCallsEvent.Delete(viewModel.calls().first()))
                    advanceUntilIdle()
                    assertInstanceOf(RecentCallsEffect.DeleteFailed::class.java, awaitItem())
                    assertEquals(listOf(2L), viewModel.calls().map { it.messageId })
                }
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(1L, 2L), repository.deleted.map { it.messageId })
        }

    @Test
    fun `calls that end join the list, missed ones only under the missed filter, and deleted ones leave it`() =
        runTest(dispatcher) {
            repository.pages[""] = page(entry(1, date = TODAY_NOON))
            val viewModel = viewModel()
            viewModel.uiState.test {
                advanceUntilIdle()
                repository.changes.emit(CallHistoryChange.Added(entry(5, date = TODAY_NOON + 60)))
                advanceUntilIdle()
                assertEquals(listOf(5L, 1L), viewModel.calls().map { it.messageId })

                repository.changes.emit(CallHistoryChange.Removed(CHAT_ID, persistentListOf(1L)))
                advanceUntilIdle()
                assertEquals(listOf(5L), viewModel.calls().map { it.messageId })

                repository.pages[""] = page(entry(5, date = TODAY_NOON + 60, outcome = CallOutcome.Missed))
                viewModel.onEvent(RecentCallsEvent.SelectFilter(CallFilter.Missed))
                advanceUntilIdle()
                repository.changes.emit(CallHistoryChange.Added(entry(6, date = TODAY_NOON + 120)))
                repository.changes.emit(
                    CallHistoryChange.Added(entry(7, date = TODAY_NOON + 180, outcome = CallOutcome.Missed)),
                )
                advanceUntilIdle()
                assertEquals(listOf(7L, 5L), viewModel.calls().map { it.messageId })
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun entry(
        messageId: Long,
        userId: Long = 42,
        date: Long,
        isOutgoing: Boolean = false,
        isVideo: Boolean = false,
        outcome: CallOutcome = CallOutcome.Answered,
        duration: Int = if (outcome == CallOutcome.Answered) 60 else 0,
    ) = CallHistoryEntry(CHAT_ID, messageId, userId, date, isOutgoing, isVideo, outcome, duration)

    private fun page(
        vararg entries: CallHistoryEntry,
        next: String = "",
    ) = CallHistoryPage(entries.toList().toImmutableList(), next)

    private companion object {
        const val CHAT_ID = 42L

        /** Noon on 2026-10-23, UTC. */
        val NOW: Instant = Instant.parse("2026-10-23T12:00:00Z")
        val TODAY_NOON = NOW.epochSecond
        val YESTERDAY_EVENING = Instant.parse("2026-10-22T19:30:00Z").epochSecond
    }
}

/** A clock at [RecentCallsViewModelTest]'s noon, in UTC. */
private object FixedClock : WallClock {
    private val now = Instant.parse("2026-10-23T12:00:00Z")

    override fun now(): Instant = now

    override fun zone(): ZoneId = ZoneOffset.UTC

    override val minutes = MutableStateFlow(now)
}

/** English patterns on the 12-hour clock, as the platform gives them for `Locale.US`. */
private object EnglishPatterns : TimePatterns {
    private val patterns =
        mapOf(
            "Hm" to "HH:mm",
            "hm" to "h:mm a",
            "MMMMEEEEd" to "EEEE, MMMM d",
            "yMMMMd" to "MMMM d, y",
        )

    override fun is24HourClock(): Boolean = false

    override fun bestPattern(
        locale: Locale,
        skeleton: String,
    ): String = patterns.getValue(skeleton)
}

/** Pages by offset, each request recorded with whether it asked for missed calls only; deletes recorded. */
private class FakeCallHistoryRepository : CallHistoryRepository {
    val pages = mutableMapOf<String, CallHistoryPage>()
    val requests = mutableListOf<Pair<String, Boolean>>()
    val deleted = mutableListOf<CallHistoryEntry>()
    var failure: DomainError? = null
    var deleteFailure: DomainError? = null
    override val changes = MutableSharedFlow<CallHistoryChange>()

    override suspend fun page(
        offset: String,
        limit: Int,
        onlyMissed: Boolean,
    ): Outcome<CallHistoryPage> {
        requests += offset to onlyMissed
        failure?.let { return Outcome.Failure(it) }
        return Outcome.Success(pages[offset] ?: CallHistoryPage(persistentListOf(), ""))
    }

    override suspend fun delete(entry: CallHistoryEntry): Outcome<Unit> {
        deleted += entry
        return deleteFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }
}
