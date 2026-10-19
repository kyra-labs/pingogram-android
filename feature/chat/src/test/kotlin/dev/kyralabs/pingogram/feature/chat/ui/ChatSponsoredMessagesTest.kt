package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AdvertisementSponsor
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessage
import dev.kyralabs.pingogram.core.domain.model.SponsoredMessages
import dev.kyralabs.pingogram.core.domain.model.SponsoredReportResult
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.MessageViews.Companion.VIEW_INTERVAL_MILLIS
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Sponsored messages in the conversation ([ChatSponsoredMessages] in [ChatViewModel]; `REQUIREMENTS.md` §2): loaded
 * once for channels and bots only, placed where the history reaches the newest message, viewed through
 * [MessageViews], opened with a click for TDLib, and "About this ad" with its report flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSponsoredMessagesTest : ChatViewModelHarness() {
    private fun post(id: Long) = message(id, CHANNEL_CHAT, sender = MessageSender.Chat(CHANNEL_CHAT))

    private fun posts(ids: LongRange): Array<Message> = ids.map(::post).toTypedArray()

    private fun ad(
        id: Long,
        canBeReported: Boolean = true,
        content: MessageContent = MessageContent.Text(text("Ad $id"), linkPreview = null),
    ) = SponsoredMessage(
        id = id,
        isRecommended = false,
        canBeReported = canBeReported,
        title = "Sponsor $id",
        content = content,
        sponsor = AdvertisementSponsor(SPONSOR_URL, photo = null, info = "Sponsor Ltd"),
        buttonText = "Open",
        accentColorId = 0,
        additionalInfo = "Paid for by Sponsor",
    )

    private fun loads(
        vararg ads: SponsoredMessage,
        messagesBetween: Int = 0,
    ) {
        sponsored.onLoad = { Outcome.Success(SponsoredMessages(ads.toList().toImmutableList(), messagesBetween)) }
    }

    /** The channel with [ids] posts, and its effects, collected from the start. */
    private fun TestScope.channel(
        ids: LongRange = 1L..3L,
        effects: MutableList<ChatEffect> = mutableListOf(),
    ): ChatViewModel {
        val viewModel = showing(channelChat, history(*posts(ids)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return viewModel
    }

    private fun TestScope.showPosts(ids: LongRange) {
        messages.setHistory(CHANNEL_CHAT, history(*posts(ids)))
        runCurrent()
    }

    private fun TestScope.wait(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Nested
    inner class Loading {
        @Test
        fun `a channel loads its sponsored messages once`() =
            runViewModelTest {
                val viewModel = channel()

                send(viewModel, ChatEvent.Started, ChatEvent.Stopped, ChatEvent.Started)

                assertEquals(listOf(CHANNEL_CHAT), sponsored.loads)
            }

        @Test
        fun `a chat with a bot loads them, a private chat and a group do not`() =
            runViewModelTest {
                users.setUsers(user(ADA, type = UserType.Bot), user(GRACE))
                showing(chat(id = ADA), history(message(1)))
                showing(chat(id = GRACE, type = ChatType.Private(GRACE)), history(message(1, chatId = GRACE)))
                showing(groupChat, history(message(1, chatId = groupChat.id)))

                assertEquals(listOf(ADA), sponsored.loads)
            }

        @Test
        fun `a failed load is tried again when the screen starts again, a successful one never`() =
            runViewModelTest {
                sponsored.onLoad = { Outcome.Failure(DomainError.Network) }
                val viewModel = channel()
                assertEquals(1, sponsored.loads.size)
                assertTrue(logged.any { "getChatSponsoredMessages" in it })

                loads(ad(100))
                send(viewModel, ChatEvent.Started)
                send(viewModel, ChatEvent.Stopped, ChatEvent.Started)

                assertEquals(2, sponsored.loads.size)
                assertEquals("ad100", viewModel.outline().first())
            }
    }

    @Nested
    inner class Placing {
        @Test
        fun `one shows below the newest message, with its date`() =
            runViewModelTest {
                loads(ad(100), ad(101))
                val viewModel = channel()

                assertEquals(listOf("ad100", "m3", "m2", "m1", "date:2026-10-18"), viewModel.outline())
                assertEquals(viewModel.bubble(3).date, viewModel.items.first().date)
            }

        @Test
        fun `with no messages between, it stays below every message as new ones come`() =
            runViewModelTest {
                loads(ad(100))
                val viewModel = channel()

                showPosts(1L..5L)

                assertEquals(listOf("ad100", "m5", "m4", "m3"), viewModel.outline().take(4))
            }

        @Test
        fun `with messages between, the next one follows that many new messages, each below its message`() =
            runViewModelTest {
                loads(ad(100), ad(101), messagesBetween = 3)
                val viewModel = channel()
                assertEquals(listOf("ad100", "m3", "m2"), viewModel.outline().take(3))

                showPosts(1L..5L)
                assertEquals(listOf("m5", "m4", "ad100", "m3"), viewModel.outline().take(4))

                showPosts(1L..6L)
                assertEquals(listOf("ad101", "m6", "m5", "m4", "ad100", "m3"), viewModel.outline().take(6))
            }

        @Test
        fun `none shows while the history does not reach the newest message`() =
            runViewModelTest {
                loads(ad(100), messagesBetween = 3)
                val viewModel = channel()

                messages.setHistory(CHANNEL_CHAT, history(*posts(1L..3L), reachedEnd = false))
                runCurrent()

                assertTrue(
                    (viewModel.state.content as? ChatContent.Content)?.items.orEmpty().none {
                        it is ChatItem.Sponsored
                    },
                )
            }
    }

    @Nested
    inner class Viewing {
        @Test
        fun `a sponsored message on screen is viewed once, only while the screen is started`() =
            runViewModelTest {
                loads(ad(100))
                val viewModel = channel()

                send(viewModel, SponsoredEvent.Viewed(100))
                assertEquals(emptyList<List<Long>>(), chats.viewed)
                send(viewModel, ChatEvent.Started)
                wait(VIEW_INTERVAL_MILLIS)
                send(viewModel, SponsoredEvent.Viewed(100))
                wait(VIEW_INTERVAL_MILLIS)

                assertEquals(listOf(listOf(100L)), chats.viewed)
                assertNull(savedState.get<Long>(ChatWindow.KEY_SHOWN_MESSAGE))
            }

        @Test
        fun `an id that is not a sponsored message is not viewed`() =
            runViewModelTest {
                loads(ad(100))
                val viewModel = channel()

                send(viewModel, ChatEvent.Started, SponsoredEvent.Viewed(7))
                wait(VIEW_INTERVAL_MILLIS)

                assertEquals(emptyList<List<Long>>(), chats.viewed)
            }
    }

    @Nested
    inner class Opening {
        @Test
        fun `the button tells TDLib and opens the sponsor's link`() =
            runViewModelTest {
                loads(ad(100))
                val effects = mutableListOf<ChatEffect>()
                val viewModel = channel(effects = effects)

                send(viewModel, SponsoredEvent.Open(100))

                assertEquals(listOf(FakeSponsoredMessageRepository.Click(CHANNEL_CHAT, 100, false)), sponsored.clicks)
                assertEquals(listOf<ChatEffect>(ChatEffect.OpenUrl(SPONSOR_URL)), effects)
            }

        @Test
        fun `the media is a media click, a link in the text opens its own link`() =
            runViewModelTest {
                val photo =
                    MessageContent.Photo(Photo(persistentListOf(), null), text("Look"), false, false, false)
                loads(ad(100, content = photo))
                val effects = mutableListOf<ChatEffect>()
                val viewModel = channel(effects = effects)

                send(viewModel, SponsoredEvent.Open(100, isMediaClick = true))
                send(viewModel, SponsoredEvent.Open(100, url = "https://example.org/offer"))

                assertEquals(listOf(true, false), sponsored.clicks.map { it.isMediaClick })
                assertEquals(
                    listOf<ChatEffect>(
                        ChatEffect.OpenUrl(SPONSOR_URL),
                        ChatEffect.OpenUrl("https://example.org/offer"),
                    ),
                    effects,
                )
            }
    }

    @Nested
    inner class AboutThisAd {
        @Test
        fun `it shows who placed the ad and more about it, and closes`() =
            runViewModelTest {
                loads(ad(100))
                val viewModel = channel()

                send(viewModel, SponsoredEvent.ShowInfo(100))
                assertEquals(
                    SponsoredSheet(100, false, "Sponsor Ltd", "Paid for by Sponsor", canBeReported = true),
                    viewModel.state.sponsoredSheet,
                )

                send(viewModel, SponsoredEvent.DismissInfo)
                assertNull(viewModel.state.sponsoredSheet)
            }

        @Test
        fun `a report that asks why shows the reasons, and the chosen one is sent`() =
            runViewModelTest {
                loads(ad(100))
                val why = ReportOptionId(byteArrayOf(9))
                sponsored.onReport = { _, option ->
                    if (option == ReportOptionId.Initial) {
                        Outcome.Success(
                            SponsoredReportResult.OptionRequired(
                                "Why?",
                                persistentListOf(
                                    ReportOption(ReportOptionId(byteArrayOf(8)), "Spam"),
                                    ReportOption(why, "Scam"),
                                ),
                            ),
                        )
                    } else {
                        Outcome.Success(SponsoredReportResult.Reported)
                    }
                }
                val effects = mutableListOf<ChatEffect>()
                val viewModel = channel(effects = effects)

                send(viewModel, SponsoredEvent.ShowInfo(100), SponsoredEvent.Report)
                assertEquals(
                    SponsoredReportStep("Why?", persistentListOf("Spam", "Scam")),
                    viewModel.state.sponsoredSheet?.report,
                )
                send(viewModel, SponsoredEvent.ChooseReportOption(1))

                assertEquals(listOf(100L to ReportOptionId.Initial, 100L to why), sponsored.reports)
                assertNull(viewModel.state.sponsoredSheet)
                assertEquals(listOf("m3", "m2", "m1"), viewModel.outline().take(3))
                assertEquals(listOf<ChatEffect>(ChatEffect.SponsoredNotice(SponsoredReportNotice.Reported)), effects)
            }

        @Test
        fun `back from the reasons returns to the information`() =
            runViewModelTest {
                loads(ad(100))
                sponsored.onReport = { _, _ ->
                    Outcome.Success(SponsoredReportResult.OptionRequired("Why?", persistentListOf()))
                }
                val viewModel = channel()

                send(viewModel, SponsoredEvent.ShowInfo(100), SponsoredEvent.Report, SponsoredEvent.BackToInfo)

                assertNull(viewModel.state.sponsoredSheet?.report)
                assertEquals(100L, viewModel.state.sponsoredSheet?.messageId)
            }

        @Test
        fun `ads hidden by Telegram all go`() =
            runViewModelTest {
                loads(ad(100), ad(101), messagesBetween = 1)
                sponsored.onReport = { _, _ -> Outcome.Success(SponsoredReportResult.AdsHidden) }
                val effects = mutableListOf<ChatEffect>()
                val viewModel = channel(effects = effects)
                showPosts(1L..4L)
                assertEquals(listOf("ad101", "m4", "ad100", "m3"), viewModel.outline().take(4))

                send(viewModel, SponsoredEvent.ShowInfo(100), SponsoredEvent.Report)

                assertTrue(viewModel.items.none { it is ChatItem.Sponsored })
                assertEquals(listOf<ChatEffect>(ChatEffect.SponsoredNotice(SponsoredReportNotice.AdsHidden)), effects)
            }

        @Test
        fun `Premium required keeps the ad and says so`() =
            runViewModelTest {
                loads(ad(100))
                sponsored.onReport = { _, _ -> Outcome.Success(SponsoredReportResult.PremiumRequired) }
                val effects = mutableListOf<ChatEffect>()
                val viewModel = channel(effects = effects)

                send(viewModel, SponsoredEvent.ShowInfo(100), SponsoredEvent.Report)

                assertEquals("ad100", viewModel.outline().first())
                assertEquals(false, viewModel.state.sponsoredSheet?.isReporting)
                assertEquals(
                    listOf<ChatEffect>(ChatEffect.SponsoredNotice(SponsoredReportNotice.PremiumRequired)),
                    effects,
                )
            }

        @Test
        fun `a flood wait is an error snackbar, and the ad stays`() =
            runViewModelTest {
                loads(ad(100))
                sponsored.onReport = { _, _ -> Outcome.Failure(DomainError.FloodWait(30)) }
                val effects = mutableListOf<ChatEffect>()
                val viewModel = channel(effects = effects)

                send(viewModel, SponsoredEvent.ShowInfo(100), SponsoredEvent.Report)

                assertEquals(listOf<ChatEffect>(ChatEffect.ShowError(DomainError.FloodWait(30))), effects)
                assertEquals("ad100", viewModel.outline().first())
            }

        @Test
        fun `an ad that cannot be reported is not`() =
            runViewModelTest {
                loads(ad(100, canBeReported = false))
                val viewModel = channel()

                send(viewModel, SponsoredEvent.ShowInfo(100), SponsoredEvent.Report)

                assertEquals(emptyList<Pair<Long, ReportOptionId>>(), sponsored.reports)
                assertEquals(false, viewModel.state.sponsoredSheet?.canBeReported)
            }
    }

    private companion object {
        const val SPONSOR_URL = "https://t.me/sponsor"
    }
}
