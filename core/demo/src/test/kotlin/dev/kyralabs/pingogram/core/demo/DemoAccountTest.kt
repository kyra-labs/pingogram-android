package dev.kyralabs.pingogram.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DemoAccountTest {
    @TempDir
    lateinit var dir: File

    private lateinit var harness: DemoTestHarness

    @BeforeEach
    fun setUp() {
        harness = DemoTestHarness(dir)
    }

    private fun TestScope.client() = with(harness) { start() }

    @Test
    fun `the user's name, bio and username change, and are kept after a restart`() =
        runTest {
            val (client, _) = client()

            client.ask(TdApi.SetName("Robin", "Sample"))
            client.ask(TdApi.SetBio("Testing the demo"))
            client.ask(TdApi.SetUsername("robin_sample"))
            val (restarted, _) = client()

            val me = restarted.ask(TdApi.GetMe())
            assertEquals("Robin" to "robin_sample", me.firstName to me.usernames?.editableUsername)
            assertEquals("Testing the demo", restarted.ask(TdApi.GetUserFullInfo(DemoData.ME)).bio?.text)
            assertEquals("Robin Sample", restarted.ask(TdApi.GetChat(DemoData.ME)).title)
        }

    @Test
    fun `a note about one of the people is kept after a restart`() =
        runTest {
            val (client, _) = client()

            client.ask(TdApi.SetUserNote(DemoData.ADA, TdApi.FormattedText("Owes me a chai", emptyArray())))
            val (restarted, _) = client()

            assertEquals("Owes me a chai", restarted.ask(TdApi.GetUserFullInfo(DemoData.ADA)).note?.text)
        }

    @Test
    fun `privacy rules start open but for the phone number, and change`() =
        runTest {
            val (client, _) = client()

            val phone = client.ask(TdApi.GetUserPrivacySettingRules(TdApi.UserPrivacySettingShowPhoneNumber()))
            client.ask(
                TdApi.SetUserPrivacySettingRules(
                    TdApi.UserPrivacySettingShowStatus(),
                    TdApi.UserPrivacySettingRules(arrayOf(TdApi.UserPrivacySettingRuleRestrictAll())),
                ),
            )

            assertInstanceOf(TdApi.UserPrivacySettingRuleAllowContacts::class.java, phone.rules.single())
            val status = client.ask(TdApi.GetUserPrivacySettingRules(TdApi.UserPrivacySettingShowStatus()))
            assertInstanceOf(TdApi.UserPrivacySettingRuleRestrictAll::class.java, status.rules.single())
        }

    @Test
    fun `a sample person's number imports them, other numbers are not on Telegram, and contacts can be removed`() =
        runTest {
            val (client, _) = client()
            val finn = client.ask(TdApi.GetUser(DemoData.FINN))

            val imported =
                client.ask(
                    TdApi.ImportContacts(
                        arrayOf(
                            TdApi.ImportedContact(finn.phoneNumber, "Finn", "Mock", null),
                            TdApi.ImportedContact("447700900999", "Nobody", "", null),
                        ),
                    ),
                )
            client.ask(TdApi.RemoveContacts(longArrayOf(DemoData.BEN)))

            assertEquals(listOf(DemoData.FINN, 0L), imported.userIds.toList())
            val contacts = client.ask(TdApi.GetContacts()).userIds.toList()
            assertTrue(DemoData.FINN in contacts && DemoData.BEN !in contacts)
        }

    @Test
    fun `a blocked person is listed until unblocked`() =
        runTest {
            val (client, _) = client()
            val dev = TdApi.MessageSenderUser(DemoData.DEV)

            client.ask(TdApi.SetMessageSenderBlockList(dev, TdApi.BlockListMain()))
            val blocked = client.ask(TdApi.GetBlockedMessageSenders(TdApi.BlockListMain(), 0, 10))
            client.ask(TdApi.SetMessageSenderBlockList(dev, null))

            assertEquals(listOf(DemoData.DEV), blocked.senders.map { (it as TdApi.MessageSenderUser).userId })
            assertEquals(0, client.ask(TdApi.GetBlockedMessageSenders(TdApi.BlockListMain(), 0, 10)).totalCount)
        }

    @Test
    fun `sessions are sample devices, another can be logged out, and a login code links one`() =
        runTest {
            val (client, _) = client()

            val before = client.ask(TdApi.GetActiveSessions()).sessions
            client.ask(TdApi.TerminateAllOtherSessions())
            client.ask(TdApi.ConfirmQrCodeAuthentication("tg://login?token=demo"))

            assertEquals(2, before.size)
            val after = client.ask(TdApi.GetActiveSessions()).sessions
            assertEquals(listOf(true, false), after.map { it.isCurrent })
            assertTrue(after.all { it.ipAddress.startsWith("192.0.2.") })
        }

    @Test
    fun `Ada's story is announced, seen once opened, and takes a reaction`() =
        runTest {
            val (client, updates) = client()

            val announced = updates.filterIsInstance<TdApi.UpdateChatActiveStories>().single().activeStories
            client.ask(TdApi.OpenStory(DemoData.ADA_CHAT, 1))
            client.ask(TdApi.SetStoryReaction(DemoData.ADA_CHAT, 1, TdApi.ReactionTypeEmoji("🔥"), false))
            runCurrent()

            assertEquals(0, announced.maxReadStoryId)
            assertEquals(
                1,
                updates
                    .filterIsInstance<TdApi.UpdateChatActiveStories>()
                    .last()
                    .activeStories.maxReadStoryId,
            )
            val story = client.ask(TdApi.GetStory(DemoData.ADA_CHAT, 1, false))
            assertInstanceOf(TdApi.StoryContentPhoto::class.java, story.content)
            assertEquals("🔥", (story.chosenReactionType as TdApi.ReactionTypeEmoji).emoji)
        }

    @Test
    fun `phone number fields get countries and a number's country`() =
        runTest {
            val (client, _) = client()

            val countries = client.ask(TdApi.GetCountries()).countries.map { it.countryCode }
            val info = client.ask(TdApi.GetPhoneNumberInfo("447700900001"))

            assertTrue("GB" in countries)
            assertEquals("GB" to "44", info.country?.countryCode to info.countryCallingCode)
        }
}
