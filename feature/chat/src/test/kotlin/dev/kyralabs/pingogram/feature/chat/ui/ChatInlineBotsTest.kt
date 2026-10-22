package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.InlineBot
import dev.kyralabs.pingogram.core.domain.model.InlineQueryResults
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButton
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButtonType
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.LocationAccess
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotUiState
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineCellSize
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlinePage
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineResultsContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Inline bots in [ChatViewModel] (`REQUIREMENTS.md` FR-C5, "@gif cats"): finding the bot, the placeholder, querying
 * after a pause with the latest query winning, pages, errors, the location question, sending a result and the bot's
 * button.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatInlineBotsTest : ChatViewModelHarness() {
    private val gif = InlineBot(GIF_BOT, GIF_BOT, "gif", "Search GIFs…", needsLocation = false)
    private val places = InlineBot(PLACES_BOT, PLACES_BOT, "places_bot", "Find a place", needsLocation = true)

    private fun animation(id: String) =
        InlineResult.Animation(
            id,
            Animation(2, 320, 240, "video/mp4", minithumbnail = null, thumbnail = null, file = FileRef(10, 100)),
            title = "Cat $id",
        )

    private fun sticker(id: String) =
        InlineResult.Sticker(id, Sticker(7, 512, 512, "😺", StickerFormat.Webp, thumbnail = null, FileRef(20, 100)))

    private fun page(
        queryId: Long,
        vararg results: InlineResult,
        nextOffset: String = "",
        button: InlineResultsButton? = null,
    ) = Outcome.Success(InlineQueryResults(queryId, button, results.toList().toImmutableList(), nextOffset))

    /** The conversation of a private chat, its inline bots @gif and @places_bot, the inline state collected. */
    private fun TestScope.conversation(
        handle: SavedStateHandle = savedState,
        topic: MessageTopic? = null,
    ): ChatViewModel {
        inlineBots.bots["gif"] = gif
        inlineBots.bots["places_bot"] = places
        chats.setChats(chat())
        messages.setHistory(ChatFixtures.PRIVATE_CHAT, history(message(1)))
        val viewModel = viewModel(handle = handle, topic = topic)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.inlineResults.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.inlineLocationSheet.collect {} }
        runCurrent()
        return viewModel
    }

    /** Replaces the composer's text, as typing does, and lets the view model see it. */
    private fun TestScope.type(
        viewModel: ChatViewModel,
        text: String,
    ) {
        viewModel.composerText.state.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    /** Types [text] and waits out the pause before a query goes. */
    private fun TestScope.query(
        viewModel: ChatViewModel,
        text: String,
    ) {
        type(viewModel, text)
        advanceTimeBy(PAUSE_MILLIS)
        runCurrent()
    }

    private val ChatViewModel.inline: InlineBotUiState? get() = inlineResults.value

    private val ChatViewModel.results get() = inline?.content as InlineResultsContent.Results

    @Nested
    inner class Finding {
        @Test
        fun `an inline bot's username and a space show its placeholder and its suggestions`() =
            runViewModelTest {
                inlineBots.onResults = { page(1, animation("a")) }
                val viewModel = conversation()

                type(viewModel, "@gif ")
                assertEquals("Search GIFs…", viewModel.inline?.placeholder)
                assertTrue(viewModel.inline!!.showsPlaceholder)
                assertEquals(InlineResultsContent.Loading, viewModel.inline?.content)

                advanceTimeBy(PAUSE_MILLIS)
                runCurrent()
                assertEquals(listOf("a"), viewModel.results.results.map { it.result.id })
                assertEquals("", inlineBots.queries.single().query)
                assertEquals(ChatFixtures.PRIVATE_CHAT, inlineBots.queries.single().chatId)
            }

        @Test
        fun `a query a bot's button puts into the field is picked up like a typed one`() =
            runViewModelTest {
                val viewModel = conversation()

                viewModel.composerText.setContent(FormattedText("@gif cats", persistentListOf()))
                Snapshot.sendApplyNotifications()
                advanceTimeBy(PAUSE_MILLIS)
                runCurrent()

                assertEquals(listOf("cats"), inlineBots.queries.map { it.query })
            }

        @Test
        fun `a query hides the placeholder`() =
            runViewModelTest {
                val viewModel = conversation()

                type(viewModel, "@gif cats")

                assertFalse(viewModel.inline!!.showsPlaceholder)
            }

        @Test
        fun `a mention, other text or a username that names no inline bot shows nothing`() =
            runViewModelTest {
                val viewModel = conversation()

                type(viewModel, "@gif")
                assertNull(viewModel.inline)
                type(viewModel, "Hi @gif cats")
                assertNull(viewModel.inline)
                query(viewModel, "@ada how are you")
                assertNull(viewModel.inline)
                assertTrue(inlineBots.queries.isEmpty())
            }

        @Test
        fun `a username is looked up once`() =
            runViewModelTest {
                val viewModel = conversation()

                type(viewModel, "@ada h")
                type(viewModel, "@ada hi")
                type(viewModel, "@gif c")
                type(viewModel, "@gif ca")

                assertEquals(listOf("ada", "gif"), inlineBots.lookups)
            }

        @Test
        fun `a lookup that fails shows nothing, and is tried again`() =
            runViewModelTest {
                inlineBots.onLookup = { Outcome.Failure(DomainError.Network) }
                val viewModel = conversation()

                type(viewModel, "@gif c")
                assertNull(viewModel.inline)
                assertTrue(logged.any { it.startsWith("Inline bot not looked up") })

                inlineBots.onLookup = null
                type(viewModel, "@gif ca")
                assertEquals("gif", viewModel.inline?.username)
            }

        @Test
        fun `nothing shows while editing a message`() =
            runViewModelTest {
                val viewModel = conversation()

                send(viewModel, ChatEvent.Edit(1))
                type(viewModel, "@gif cats")

                assertNull(viewModel.inline)
            }

        @Test
        fun `the query in the field comes back after process death`() =
            runViewModelTest {
                val first = conversation()
                type(first, "@gif cats")
                clear(first)

                val restored =
                    conversation(handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
                advanceTimeBy(PAUSE_MILLIS)
                runCurrent()

                assertEquals("cats", restored.inline?.query)
            }
    }

    @Nested
    inner class Querying {
        @Test
        fun `a query waits for a pause, and the latest wins`() =
            runViewModelTest {
                val viewModel = conversation()

                type(viewModel, "@gif c")
                advanceTimeBy(PAUSE_MILLIS / 2)
                type(viewModel, "@gif ca")
                advanceTimeBy(PAUSE_MILLIS / 2)
                type(viewModel, "@gif cat")
                advanceTimeBy(PAUSE_MILLIS)
                runCurrent()

                assertEquals(listOf("cat"), inlineBots.queries.map { it.query })
            }

        @Test
        fun `results stay while a newer query is on its way`() =
            runViewModelTest {
                inlineBots.onResults = { page(1, animation(it.query)) }
                val viewModel = conversation()
                query(viewModel, "@gif cat")

                type(viewModel, "@gif cats")
                assertTrue(viewModel.results.isSearching)
                assertEquals(listOf("cat"), viewModel.results.results.map { it.result.id })

                advanceTimeBy(PAUSE_MILLIS)
                runCurrent()
                assertFalse(viewModel.results.isSearching)
                assertEquals(listOf("cats"), viewModel.results.results.map { it.result.id })
            }

        @Test
        fun `nothing found says so, with the bot's button`() =
            runViewModelTest {
                val button = InlineResultsButton("Sign in", InlineResultsButtonType.StartBot("login"))
                inlineBots.onResults = { page(1, button = button) }
                val viewModel = conversation()

                query(viewModel, "@gif zzz")

                assertEquals(InlineResultsContent.Empty(button), viewModel.inline?.content)
            }

        @Test
        fun `stickers alone take the sticker cells`() =
            runViewModelTest {
                inlineBots.onResults = { page(1, sticker("s1"), sticker("s2")) }
                val viewModel = conversation()

                query(viewModel, "@gif cat")

                assertEquals(InlineCellSize.Stickers, viewModel.results.cells)
            }

        @Test
        fun `the next page loads as the list nears its end, with its own query id`() =
            runViewModelTest {
                inlineBots.onResults = { query ->
                    if (query.offset.isEmpty()) {
                        page(1, animation("a"), animation("b"), nextOffset = "2")
                    } else {
                        page(2, animation("b"), animation("c"))
                    }
                }
                val viewModel = conversation()
                query(viewModel, "@gif cat")
                assertEquals(InlinePage.More, viewModel.results.page)

                send(viewModel, InlineBotEvent.LoadMore)

                assertEquals(listOf("", "2"), inlineBots.queries.map { it.offset })
                assertEquals(
                    listOf(1L to "a", 1L to "b", 2L to "c"),
                    viewModel.results.results.map { it.queryId to it.result.id },
                )
                assertEquals(InlinePage.Done, viewModel.results.page)
            }

        @Test
        fun `a failed page keeps the others and loads again on Try again`() =
            runViewModelTest {
                var failing = true
                inlineBots.onResults = { query ->
                    when {
                        query.offset.isEmpty() -> page(1, animation("a"), nextOffset = "2")
                        failing -> Outcome.Failure(DomainError.Network)
                        else -> page(2, animation("b"))
                    }
                }
                val viewModel = conversation()
                query(viewModel, "@gif cat")

                send(viewModel, InlineBotEvent.LoadMore)
                assertEquals(InlinePage.Failed(DomainError.Network), viewModel.results.page)
                assertEquals(1, viewModel.results.results.size)

                failing = false
                send(viewModel, InlineBotEvent.LoadMore)
                assertEquals(2, viewModel.results.results.size)
            }

        @Test
        fun `a network error shows with Try again, which asks again`() =
            runViewModelTest {
                inlineBots.onResults = { Outcome.Failure(DomainError.Network) }
                val viewModel = conversation()
                query(viewModel, "@gif cat")
                val failed = viewModel.inline?.content as InlineResultsContent.Failed
                assertEquals(DomainError.Network, failed.error)
                assertTrue(failed.canRetry)

                inlineBots.onResults = { page(1, animation("a")) }
                send(viewModel, InlineBotEvent.Retry)
                advanceTimeBy(PAUSE_MILLIS)
                runCurrent()

                assertEquals(1, viewModel.results.results.size)
            }

        @Test
        fun `a flood wait counts down before Try again may ask, and asks nothing by itself`() =
            runViewModelTest {
                inlineBots.onResults = { Outcome.Failure(DomainError.FloodWait(3)) }
                val viewModel = conversation()
                query(viewModel, "@gif cat")
                assertEquals(InlineResultsContent.Failed(DomainError.FloodWait(3)), viewModel.inline?.content)
                assertFalse((viewModel.inline?.content as InlineResultsContent.Failed).canRetry)

                advanceTimeBy(2_000)
                runCurrent()
                assertEquals(InlineResultsContent.Failed(DomainError.FloodWait(1)), viewModel.inline?.content)

                advanceTimeBy(5_000)
                runCurrent()
                assertTrue((viewModel.inline?.content as InlineResultsContent.Failed).canRetry)
                assertEquals(1, inlineBots.queries.size)
            }

        @Test
        fun `an unauthorized session shows its error`() =
            runViewModelTest {
                inlineBots.onResults = { Outcome.Failure(DomainError.Unauthorized) }
                val viewModel = conversation()

                query(viewModel, "@gif cat")

                assertEquals(InlineResultsContent.Failed(DomainError.Unauthorized), viewModel.inline?.content)
            }
    }

    @Nested
    inner class Sending {
        @Test
        fun `a result is sent with the reply, and the field clears`() =
            runViewModelTest {
                inlineBots.onResults = { page(5, animation("a")) }
                val viewModel = conversation()
                send(viewModel, ChatEvent.Reply(1))
                query(viewModel, "@gif cat")

                send(viewModel, InlineBotEvent.Send(5, "a", SendOptions(silent = true)))
                Snapshot.sendApplyNotifications()
                runCurrent()

                assertEquals(
                    FakeInlineBotRepository.Sent(ChatFixtures.PRIVATE_CHAT, 5, "a", SendOptions(silent = true), 1),
                    inlineBots.sent.single(),
                )
                assertEquals("", viewModel.composerText.content.text)
                assertNull((viewModel.state.composer as ComposerUiState.Available).bar)
                assertNull(viewModel.inline)
            }

        @Test
        fun `a scheduled result says when it goes out`() =
            runViewModelTest {
                inlineBots.onResults = { page(5, animation("a")) }
                val viewModel = conversation()
                query(viewModel, "@gif cat")

                viewModel.effects.test {
                    send(viewModel, InlineBotEvent.Send(5, "a", SendOptions(scheduledAt = 1_900_000_000)))

                    assertEquals(ChatEffect.MessageScheduled(1_900_000_000), awaitItem())
                }
            }

        @Test
        fun `a refused result gives the text and the reply back, with a snackbar`() =
            runViewModelTest {
                inlineBots.onResults = { page(5, animation("a")) }
                inlineBots.onSend = { Outcome.Failure(DomainError.FloodWait(30)) }
                val viewModel = conversation()
                send(viewModel, ChatEvent.Reply(1))
                query(viewModel, "@gif cat")

                viewModel.effects.test {
                    send(viewModel, InlineBotEvent.Send(5, "a"))

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(30)), expectMostRecentItem())
                }
                assertEquals("@gif cat", viewModel.composerText.content.text)
                assertEquals(1L, (viewModel.state.composer as ComposerUiState.Available).bar?.messageId)
            }

        @Test
        fun `a game is not sent`() =
            runViewModelTest {
                inlineBots.onResults = { page(5, InlineResult.Game("g", "Chess", "", photo = null)) }
                val viewModel = conversation()
                query(viewModel, "@gif chess")

                viewModel.effects.test {
                    send(viewModel, InlineBotEvent.Send(5, "g"))

                    assertEquals(ChatEffect.GamesUnsupported, awaitItem())
                }
                assertTrue(inlineBots.sent.isEmpty())
                assertEquals("@gif chess", viewModel.composerText.content.text)
            }

        @Test
        fun `in a topic a result goes into the topic`() =
            runViewModelTest {
                val topic = MessageTopic.Thread(9)
                inlineBots.onResults = { page(5, animation("a")) }
                val viewModel = conversation(topic = topic)
                query(viewModel, "@gif cat")

                send(viewModel, InlineBotEvent.Send(5, "a"))

                assertEquals(SendOptions(topic = topic), inlineBots.sent.single().options)
            }
    }

    @Nested
    inner class Button {
        @Test
        fun `Start opens the bot's chat with its parameter, sending nothing`() =
            runViewModelTest {
                val button = InlineResultsButton("Sign in", InlineResultsButtonType.StartBot("login"))
                inlineBots.onResults = { page(5, animation("a"), button = button) }
                val viewModel = conversation()
                query(viewModel, "@gif cat")

                viewModel.effects.test {
                    send(viewModel, InlineBotEvent.OpenButton)

                    assertEquals(ChatEffect.OpenChat(GIF_BOT), awaitItem())
                }
                assertTrue(botStarts.take(GIF_BOT, "login"))
                assertTrue(bots.started.isEmpty())
            }

        @Test
        fun `a Mini App's button says Mini Apps are not supported yet`() =
            runViewModelTest {
                val button = InlineResultsButton("Open", InlineResultsButtonType.WebApp)
                inlineBots.onResults = { page(5, animation("a"), button = button) }
                val viewModel = conversation()
                query(viewModel, "@gif cat")

                viewModel.effects.test {
                    send(viewModel, InlineBotEvent.OpenButton)

                    assertEquals(ChatEffect.MiniAppsUnsupported, awaitItem())
                }
            }
    }

    @Nested
    inner class SharingLocation {
        private val here = DeviceLocation(Location(52.5, 13.4), accuracyMeters = 20f)

        @Test
        fun `a bot that asks for the location is asked about first, and nothing is queried meanwhile`() =
            runViewModelTest {
                val viewModel = conversation()

                query(viewModel, "@places_bot cafe")

                assertEquals("places_bot", viewModel.inlineLocationSheet.value?.username)
                assertFalse(viewModel.inlineLocationSheet.value!!.needsPermission)
                assertTrue(inlineBots.queries.isEmpty())
            }

        @Test
        fun `sharing sends the device's location with its queries`() =
            runViewModelTest {
                val viewModel = conversation()
                query(viewModel, "@places_bot cafe")

                send(viewModel, InlineBotEvent.AnswerLocation(share = true))
                deviceLocation.send(here)
                advanceTimeBy(PAUSE_MILLIS)
                runCurrent()

                assertNull(viewModel.inlineLocationSheet.value)
                assertEquals(Location(52.5, 13.4), inlineBots.queries.single().location)
                assertEquals(0, deviceLocation.searching)
            }

        @Test
        fun `Not now queries without the location, and is not asked again`() =
            runViewModelTest {
                val viewModel = conversation()
                query(viewModel, "@places_bot cafe")

                send(viewModel, InlineBotEvent.AnswerLocation(share = false))
                advanceTimeBy(PAUSE_MILLIS)
                runCurrent()
                assertNull(inlineBots.queries.single().location)
                assertEquals(0, deviceLocation.searches)

                query(viewModel, "@places_bot bar")
                assertNull(viewModel.inlineLocationSheet.value)
                assertEquals(2, inlineBots.queries.size)
            }

        @Test
        fun `the answer holds for other conversations of the process`() =
            runViewModelTest {
                locationConsent.set(PLACES_BOT, share = false)
                val viewModel = conversation()

                query(viewModel, "@places_bot cafe")

                assertNull(viewModel.inlineLocationSheet.value)
                assertNull(inlineBots.queries.single().location)
            }

        @Test
        fun `without Android's permission no location goes, even with consent`() =
            runViewModelTest {
                deviceLocation.access = LocationAccess.None
                val viewModel = conversation()
                query(viewModel, "@places_bot cafe")
                assertTrue(viewModel.inlineLocationSheet.value!!.needsPermission)

                send(viewModel, InlineBotEvent.AnswerLocation(share = true))
                advanceTimeBy(PAUSE_MILLIS)
                runCurrent()

                assertNull(inlineBots.queries.single().location)
                assertEquals(0, deviceLocation.searches)
            }

        @Test
        fun `bots that do not ask never get the location`() =
            runViewModelTest {
                locationConsent.set(GIF_BOT, share = true)
                val viewModel = conversation()

                query(viewModel, "@gif cat")

                assertNull(viewModel.inlineLocationSheet.value)
                assertNull(inlineBots.queries.single().location)
                assertEquals(0, deviceLocation.searches)
            }

        @Test
        fun `deleting the query closes the question`() =
            runViewModelTest {
                val viewModel = conversation()
                query(viewModel, "@places_bot cafe")

                type(viewModel, "")

                assertNull(viewModel.inlineLocationSheet.value)
                assertNull(locationConsent.answer(PLACES_BOT))
            }
    }

    private companion object {
        const val GIF_BOT = 900L
        const val PLACES_BOT = 901L

        /** A little more than the pause before a query goes. */
        const val PAUSE_MILLIS = 401L
    }
}
