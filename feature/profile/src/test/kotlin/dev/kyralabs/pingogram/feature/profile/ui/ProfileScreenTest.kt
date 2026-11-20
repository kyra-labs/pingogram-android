package dev.kyralabs.pingogram.feature.profile.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.text.LocalPhoneNumbersHidden
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The profile's actions and rows send their events, and those that do not apply are not there. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProfileScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ProfileEvent>()
    private val mediaEvents = mutableListOf<SharedMediaEvent>()
    private val opened = mutableListOf<String>()
    private var editedContacts = 0

    private fun setScreen(
        state: ProfileUiState = ProfilePreviewData.content,
        media: SharedMediaUiState = ProfilePreviewData.media,
        withContactEditor: Boolean = true,
        numbersHidden: Boolean = false,
        stories: ProfileStoriesSlot? = null,
        profileMusic: ProfileMusicSlot? = null,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalPhoneNumbersHidden provides numbersHidden) {
                    ProfileScreen(
                        state = state,
                        media = media,
                        onEvent = { events += it },
                        onMediaEvent = { mediaEvents += it },
                        onBack = {},
                        mediaActions =
                            SharedMediaActions(
                                onOpenMedia = { chatId, messageId -> opened += "media:$chatId:$messageId" },
                                onOpenMessage = { chatId, messageId -> opened += "message:$chatId:$messageId" },
                                onOpenUrl = { opened += "url:$it" },
                            ),
                        onEditContact = if (withContactEditor) ({ editedContacts++ }) else null,
                        stories = stories,
                        profileMusic = profileMusic,
                    )
                }
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = composeRule.activity.getString(id)

    /** The block row's label as the screen builds it: "Block Ada", the name and the whole label isolated. */
    private fun blockLabel(name: String) =
        composeRule.activity.getString(R.string.profile_block_person, name.bidiIsolated()).bidiIsolated()

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(PROFILE_GRID_TEST_TAG).performScrollToNode(hasText(text))
    }

    /** A Stories tab's slot whose items say whose stories they are and whether the tab is selected. */
    private fun storiesSlot(asked: MutableList<Pair<Long, Boolean>>): ProfileStoriesSlot =
        { chatId, active ->
            asked += chatId to active
            { _, _ -> item(key = "stories") { Text("Stories of $chatId") } }
        }

    @Test
    fun `a person's Stories tab comes first and shows its items in place of the media once selected`() {
        val asked = mutableListOf<Pair<Long, Boolean>>()
        setScreen(stories = storiesSlot(asked))
        val chatId = ProfilePreviewData.media.chatId

        scrollTo(string(R.string.profile_stories))
        composeRule.onNodeWithText(string(R.string.profile_stories)).performClick()
        scrollTo("Stories of $chatId")

        composeRule.onNodeWithText("Stories of $chatId").assertIsDisplayed()
        assertEquals(listOf(chatId to false, chatId to true), asked.distinct())
        composeRule.onNodeWithText(string(R.string.profile_stories)).assertIsOn()
        composeRule.assertAccessible()
    }

    @Test
    fun `a bot's profile has no Stories tab`() {
        val asked = mutableListOf<Pair<Long, Boolean>>()
        val content = ProfilePreviewData.content
        setScreen(state = content.copy(actions = content.actions.copy(isBot = true)), stories = storiesSlot(asked))

        scrollTo(string(R.string.profile_media_photos))
        composeRule.onNodeWithText(string(R.string.profile_stories)).assertDoesNotExist()
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `the profile music slot shows for the profile's user, saying whether it is the user's own`() {
        val asked = mutableListOf<Pair<Long, Boolean>>()
        setScreen(
            profileMusic = { userId, isSelf, modifier ->
                asked += userId to isSelf
                Text("Music of $userId", modifier)
            },
        )

        val content = ProfilePreviewData.content
        composeRule.onNodeWithText("Music of ${content.header.userId}").assertIsDisplayed()
        assertEquals(content.header.userId to content.actions.isSelf, asked.distinct().single())
    }

    @Test
    fun `scrolling down, up and down again brings the header and the actions back each time`() {
        // Another user without songs: the music slot draws nothing.
        setScreen(profileMusic = { _, _, _ -> })
        val grid = composeRule.onNodeWithTag(PROFILE_GRID_TEST_TAG)
        val avatar =
            hasContentDescription(composeRule.activity.getString(R.string.profile_photo_description, "Ada Lovelace"))
        val message = hasText(string(R.string.profile_action_message))

        repeat(2) {
            grid.performTouchInput { swipeUp() }
            composeRule.onNode(avatar).assertIsNotDisplayed()

            grid.performTouchInput { swipeDown() }
            composeRule.onNode(avatar).assertIsDisplayed()
            composeRule.onNode(message).assertIsDisplayed()
        }
    }

    @Test
    fun `the name is a heading`() {
        setScreen()

        composeRule.onNode(isHeading() and hasText("Ada Lovelace", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `the note row shows the note and opens its dialog`() {
        setScreen(state = ProfilePreviewData.withNote)
        val note = "Met at the Analytical Society. Owes me a chai. Prefers letters."

        scrollTo(note)
        composeRule.onNodeWithText(string(R.string.profile_note_private)).assertIsDisplayed()
        composeRule.onNodeWithText(note).performClick()

        assertEquals(listOf<ProfileEvent>(ProfileEvent.EditNote), events)
    }

    @Test
    fun `without a note the row offers to add one`() {
        setScreen(state = ProfilePreviewData.withoutNote)

        scrollTo(string(R.string.profile_note_add))
        composeRule.onNodeWithText(string(R.string.profile_note_add)).assertIsDisplayed()
    }

    @Test
    fun `a contact without a birthday offers to suggest one, in a picker titled so`() {
        setScreen(state = ProfilePreviewData.withBirthdaySuggestion)
        val action = string(R.string.profile_birthday_suggest_action)

        scrollTo(action)
        composeRule.onNode(hasText(action) and hasClickAction()).performClick()

        composeRule.onNodeWithText(string(R.string.profile_birthday_suggest_confirm)).assertIsDisplayed()
        assertEquals(emptyList<ProfileEvent>(), events)
    }

    @Test
    fun `no note row on a profile without one`() {
        setScreen()

        val rows = composeRule.onAllNodesWithText(string(R.string.profile_note_private)).fetchSemanticsNodes()
        assertEquals(0, rows.size)
    }

    @Test
    fun `the action pills send their events`() {
        setScreen()

        composeRule.onNode(hasText(string(R.string.profile_action_message)) and hasClickAction()).performClick()
        composeRule.onNode(hasText(string(R.string.profile_action_mute)) and hasClickAction()).performClick()

        assertEquals(listOf(ProfileEvent.SendMessage, ProfileEvent.ToggleMute), events)
    }

    @Test
    fun `secret chat is in the more options menu, not among the pills`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.profile_action_secret_chat)).assertDoesNotExist()
        composeRule.onNode(hasContentDescription(string(R.string.profile_more_options))).performClick()
        composeRule.onNodeWithText(string(R.string.profile_action_secret_chat)).performClick()

        assertEquals(listOf<ProfileEvent>(ProfileEvent.StartSecretChat), events)
    }

    @Test
    fun `a bot's pills are Message, Mute and Share, which shares its link`() {
        setScreen(ProfilePreviewData.bot, ProfilePreviewData.emptyMedia)

        composeRule.onNodeWithText(string(R.string.profile_action_share)).performClick()

        assertEquals(listOf<ProfileEvent>(ProfileEvent.ShareUsername("stickerfinderbot")), events)
        composeRule.onNodeWithText(string(R.string.profile_action_call)).assertDoesNotExist()
    }

    @Test
    fun `tapping a username copies it, its button shares it`() {
        setScreen()

        scrollTo("@ada")
        composeRule.onNodeWithText("@ada").performClick()
        composeRule.onNode(hasContentDescription(string(R.string.profile_share_username))).performClick()

        assertEquals(listOf(ProfileEvent.CopyUsername("ada"), ProfileEvent.ShareUsername("ada")), events)
    }

    @Test
    fun `tapping the phone number dials it, its button copies it`() {
        setScreen()

        scrollTo(string(R.string.profile_phone))
        composeRule.onNode(hasText(string(R.string.profile_phone)), useUnmergedTree = true).performClick()
        composeRule.onNode(hasContentDescription(string(R.string.profile_copy_phone))).performClick()

        assertEquals(listOf(ProfileEvent.CallPhone, ProfileEvent.CopyPhone), events)
    }

    @Test
    fun `a hidden phone number shows its calling code only, and still dials and copies`() {
        setScreen(numbersHidden = true)

        scrollTo(string(R.string.profile_phone))
        composeRule.onNodeWithText("+44 •••• ••••••", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(
            composeRule
                .onAllNodesWithText(
                    "7700",
                    substring = true,
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                .isEmpty(),
        )
        composeRule.onNode(hasContentDescription(string(R.string.profile_copy_phone))).performClick()

        assertEquals(listOf(ProfileEvent.CopyPhone), events)
    }

    @Test
    fun `the rows below edit the contact, block and report`() {
        setScreen()

        scrollTo(string(R.string.profile_report))
        composeRule.onNodeWithText(string(R.string.profile_edit_contact)).performClick()
        composeRule.onNodeWithText(blockLabel("Ada")).performClick()
        composeRule.onNodeWithText(string(R.string.profile_report)).performClick()

        assertEquals(1, editedContacts)
        assertEquals(listOf(ProfileEvent.Block, ProfileEvent.Report), events)
    }

    @Test
    fun `the stranger card blocks, reports and adds the contact`() {
        setScreen(ProfilePreviewData.stranger, ProfilePreviewData.emptyMedia)

        composeRule.onNodeWithText("isn't in your contacts", substring = true).assertExists()
        composeRule.onNodeWithText("2 groups in common").assertExists()
        composeRule.onNodeWithText(string(R.string.profile_stranger_block)).performClick()
        // The card's "Report" comes first; the More group repeats it below.
        composeRule.onAllNodesWithText(string(R.string.profile_report))[0].performClick()
        composeRule.onNodeWithText(string(R.string.profile_stranger_add_contact)).performClick()

        assertEquals(listOf(ProfileEvent.Block, ProfileEvent.Report), events)
        assertEquals(1, editedContacts)
    }

    @Test
    fun `a phone number hidden by privacy settings says so`() {
        setScreen(ProfilePreviewData.stranger, ProfilePreviewData.emptyMedia)

        composeRule.onNodeWithText("s privacy settings", substring = true).assertExists()
    }

    @Test
    fun `without a contact editor the contact row is left out`() {
        setScreen(withContactEditor = false)

        scrollTo(string(R.string.profile_report))

        composeRule.onNodeWithText(string(R.string.profile_edit_contact)).assertDoesNotExist()
    }

    @Test
    fun `a deleted account offers only deleting the chat, and says what is kept`() {
        setScreen(state = ProfilePreviewData.deleted, media = ProfilePreviewData.emptyMedia)

        composeRule.onNodeWithText("Block", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.profile_report)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.profile_action_secret_chat)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.profile_action_message)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.profile_action_mute)).assertDoesNotExist()
        composeRule.onNode(hasContentDescription(string(R.string.profile_more_options))).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.profile_deleted_title)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.profile_action_delete_chat)).performClick()
        assertTrue(events.toString(), ProfileEvent.DeleteChat in events)
    }

    @Test
    fun `a tab selects its kind, and a photo opens in the media viewer`() {
        setScreen()

        scrollTo(string(R.string.profile_media_files))
        composeRule.onNode(hasText(string(R.string.profile_media_files)) and hasClickAction()).performClick()
        assertTrue(mediaEvents.toString(), SharedMediaEvent.Select(SharedMediaKind.Files) in mediaEvents)

        val photo = composeRule.activity.getString(R.string.profile_media_photo, "Mar 17")
        composeRule.onNodeWithTag(PROFILE_GRID_TEST_TAG).performScrollToNode(hasContentDescription(photo))
        composeRule.onAllNodes(hasContentDescription(photo))[0].performClick()
        assertEquals("media:42:1", opened.single())
    }

    @Test
    fun `a file opens in the chat and a link in the browser`() {
        setScreen(state = ProfilePreviewData.scam, media = ProfilePreviewData.files)
        scrollTo("Notes on the Engine.pdf")
        composeRule.onNodeWithText("Notes on the Engine.pdf").performClick()

        assertEquals(listOf("message:42:1"), opened)
    }

    @Test
    fun `the grid asks for more as its end comes near`() {
        setScreen()

        composeRule.waitForIdle()

        assertTrue(mediaEvents.toString(), SharedMediaEvent.LoadMore in mediaEvents)
    }
}
