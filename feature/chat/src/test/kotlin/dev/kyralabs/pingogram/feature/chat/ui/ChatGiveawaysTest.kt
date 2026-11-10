package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AppInfo
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayParticipantStatus
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.richMediaId
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.content.PaidPostSamples
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.ChatGiveaways
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.GiveawayInfoState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Giveaways and paid posts in [ChatViewModel] (`REQUIREMENTS.md` FR-C4): the details sheet asks TDLib only when it
 * opens, shows what it knows or why it failed, asks again on "Try again", closes, and opens again after process death;
 * the bubble names the channels and the winners; a channel's chip opens the chat; a paid post's media open in the
 * viewer; "Update Pingogram" opens the releases page and is left out of store builds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatGiveawaysTest : ChatViewModelHarness() {
    private val effects = mutableListOf<ChatEffect>()

    private val channel =
        chat(
            id = CHANNEL,
            type = ChatType.Supergroup(SUPERGROUP_ID, isChannel = true),
            title = "Android Devs Weekly",
        )

    /** The channel with [content] as message [MESSAGE] (and the winners as [WINNERS_MESSAGE]). */
    private fun TestScope.conversation(
        content: MessageContent = PaidPostSamples.giveaway(),
        handle: SavedStateHandle = savedState,
        appInfo: AppInfo = AppInfo(versionName = ""),
    ): ChatViewModel {
        users.setUsers(
            user(ME, firstName = "Me"),
            user(PaidPostSamples.WINNER_RAVI, firstName = "Ravi"),
            user(PaidPostSamples.WINNER_MEERA, firstName = "Meera"),
        )
        chats.setChats(channel)
        messages.setHistory(
            CHANNEL,
            history(
                message(
                    WINNERS_MESSAGE,
                    CHANNEL,
                    MessageSender.Chat(CHANNEL),
                    content = PaidPostSamples.giveawayWinners(),
                ),
                message(MESSAGE, CHANNEL, MessageSender.Chat(CHANNEL), content = content),
            ),
        )
        val viewModel = viewModel(CHANNEL, handle = handle, appInfo = appInfo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel
    }

    @Test
    fun `the bubble names the participating channels and the winners it knows`() =
        runViewModelTest {
            val viewModel = conversation()

            val giveaway = viewModel.bubble(MESSAGE).giveaway
            val winners = viewModel.bubble(WINNERS_MESSAGE).giveaway

            assertEquals(listOf(CHANNEL, PaidPostSamples.OTHER_CHANNEL), giveaway?.channels?.map { it.id })
            assertEquals(
                listOf(UiText.Plain("Android Devs Weekly"), res(R.string.giveaway_channel_unknown)),
                giveaway!!.channels.map { it.name },
            )
            assertEquals(
                listOf(
                    PaidPostSamples.WINNER_RAVI,
                    PaidPostSamples.WINNER_MEERA,
                    PaidPostSamples.WINNER_JONAS,
                ),
                winners?.winners?.map {
                    it.id
                },
            )
            assertEquals(
                listOf(UiText.Plain("Ravi"), UiText.Plain("Meera"), res(R.string.giveaway_winner_unknown)),
                winners!!.winners.map { it.name },
            )
            assertTrue(giveaways.requests.isEmpty())
        }

    @Test
    fun `Learn more asks TDLib and shows what it knows`() =
        runViewModelTest {
            val answer = CompletableDeferred<Outcome<GiveawayInfo>>()
            giveaways.onInfo = { answer.await() }
            val viewModel = conversation()

            send(viewModel, GiveawayEvent.OpenInfo(MESSAGE))
            val loading = viewModel.giveawayInfoSheet.value?.state
            val info = GiveawayInfo.Ongoing(1L, GiveawayParticipantStatus.Participating, isEnded = false)
            answer.complete(Outcome.Success(info))
            runCurrent()

            assertEquals(GiveawayInfoState.Loading, loading)
            assertEquals(listOf(CHANNEL to MESSAGE), giveaways.requests)
            assertEquals(GiveawayInfoState.Loaded(info), viewModel.giveawayInfoSheet.value?.state)
            assertEquals(PaidPostSamples.WINNERS, viewModel.giveawayInfoSheet.value?.winnerCount)
        }

    @Test
    fun `a failure shows in the sheet, and Try again asks again`() =
        runViewModelTest {
            giveaways.onInfo = { Outcome.Failure(DomainError.Network) }
            val viewModel = conversation()

            send(viewModel, GiveawayEvent.OpenInfo(MESSAGE))
            val failed = viewModel.giveawayInfoSheet.value?.state
            giveaways.onInfo = { Outcome.Success(GiveawayInfo.Ongoing(1L, GiveawayParticipantStatus.Eligible, false)) }
            send(viewModel, GiveawayEvent.RetryInfo)

            assertEquals(GiveawayInfoState.Failed(DomainError.Network), failed)
            assertTrue(viewModel.giveawayInfoSheet.value?.state is GiveawayInfoState.Loaded)
            assertEquals(2, giveaways.requests.size)
            assertTrue(effects.isEmpty())
        }

    @Test
    fun `the administrator's chat is named`() =
        runViewModelTest {
            giveaways.onInfo = {
                Outcome.Success(GiveawayInfo.Ongoing(1L, GiveawayParticipantStatus.Administrator(CHANNEL), false))
            }
            val viewModel = conversation()

            send(viewModel, GiveawayEvent.OpenInfo(MESSAGE))

            assertEquals(
                "Android Devs Weekly",
                (viewModel.giveawayInfoSheet.value?.state as GiveawayInfoState.Loaded).chatTitle,
            )
        }

    @Test
    fun `dismissing closes the sheet, and a message that is no giveaway opens none`() =
        runViewModelTest {
            val viewModel = conversation()

            send(viewModel, GiveawayEvent.OpenInfo(MESSAGE), GiveawayEvent.DismissInfo)
            assertNull(viewModel.giveawayInfoSheet.value)

            send(viewModel, GiveawayEvent.OpenInfo(99L shl 20))
            assertNull(viewModel.giveawayInfoSheet.value)
            assertEquals(listOf(ChatEffect.ActionUnavailable), effects)
        }

    @Test
    fun `the open sheet comes back after process death`() =
        runViewModelTest {
            val viewModel = conversation()
            send(viewModel, GiveawayEvent.OpenInfo(WINNERS_MESSAGE))

            val restored =
                conversation(handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(WINNERS_MESSAGE, restored.giveawayInfoSheet.value?.messageId)
            assertEquals(2, giveaways.requests.size)
        }

    @Test
    fun `a channel's chip opens the chat`() =
        runViewModelTest {
            val viewModel = conversation()

            send(viewModel, ChatEvent.OpenChat(PaidPostSamples.OTHER_CHANNEL))

            assertEquals(listOf(ChatEffect.OpenChat(PaidPostSamples.OTHER_CHANNEL)), effects)
        }

    @Test
    fun `a paid post's media open in the viewer once paid for, never while locked`() =
        runViewModelTest {
            val mediaId = richMediaId(MESSAGE, 1)
            val unlocked = conversation(content = PaidPostSamples.paidMedia(unlocked = true))
            assertTrue((unlocked.bubble(MESSAGE).content as MessageContent.PaidMedia).isUnlocked)
            send(unlocked, ChatEvent.OpenMedia(mediaId))
            assertEquals(listOf<ChatEffect>(ChatEffect.OpenMedia(mediaId)), effects)
            clear(unlocked)

            val locked = conversation(content = PaidPostSamples.paidMedia(unlocked = false))
            send(locked, ChatEvent.OpenMedia(mediaId))

            assertEquals(listOf<ChatEffect>(ChatEffect.OpenMedia(mediaId)), effects)
        }

    @Test
    fun `Update Pingogram opens the latest release`() =
        runViewModelTest {
            val viewModel = conversation(content = MessageContent.Unsupported)

            send(viewModel, ChatEvent.OpenUpdatePage)

            assertTrue(viewModel.uiState.value.showsUpdateLink)
            assertEquals(listOf(ChatEffect.OpenUrl(ChatViewModel.LATEST_RELEASE_URL)), effects)
        }

    @Test
    fun `store builds leave the update link out`() =
        runViewModelTest {
            val store =
                conversation(
                    content = MessageContent.Unsupported,
                    appInfo = AppInfo("1.0", installer = "com.android.vending"),
                )

            assertFalse(store.uiState.value.showsUpdateLink)
        }

    private companion object {
        const val CHANNEL = PaidPostSamples.CHANNEL
        // Telegram's message ids are multiples of 2^20, which a paid post's media ids rely on (richMediaId).
        const val MESSAGE = 10L shl 20
        const val WINNERS_MESSAGE = 11L shl 20
    }
}
