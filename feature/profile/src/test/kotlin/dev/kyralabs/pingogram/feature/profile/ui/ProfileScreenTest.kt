package dev.kyralabs.pingogram.feature.profile.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
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
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.ui.text.LocalPhoneNumbersHidden
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
                    )
                }
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = composeRule.activity.getString(id)

    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(PROFILE_GRID_TEST_TAG).performScrollToNode(hasText(text))
    }

    @Test
    fun `the name is a heading`() {
        setScreen()

        composeRule.onNode(isHeading() and hasText("Ada Lovelace", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `the action pills send their events`() {
        setScreen()

        composeRule.onNode(hasText(string(R.string.profile_action_message)) and hasClickAction()).performClick()
        composeRule.onNode(hasText(string(R.string.profile_action_mute)) and hasClickAction()).performClick()
        composeRule.onNode(hasText(string(R.string.profile_action_secret_chat)) and hasClickAction()).performClick()

        assertEquals(listOf(ProfileEvent.SendMessage, ProfileEvent.ToggleMute, ProfileEvent.StartSecretChat), events)
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
        composeRule.onNodeWithText(string(R.string.profile_block)).performClick()
        composeRule.onNodeWithText(string(R.string.profile_report)).performClick()

        assertEquals(1, editedContacts)
        assertEquals(listOf(ProfileEvent.Block, ProfileEvent.Report), events)
    }

    @Test
    fun `without a contact editor the contact row is left out`() {
        setScreen(withContactEditor = false)

        scrollTo(string(R.string.profile_report))

        composeRule.onNodeWithText(string(R.string.profile_edit_contact)).assertDoesNotExist()
    }

    @Test
    fun `a deleted account offers neither blocking nor reporting`() {
        setScreen(state = ProfilePreviewData.deleted, media = ProfilePreviewData.emptyMedia)

        composeRule.onNodeWithText(string(R.string.profile_block)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.profile_report)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.profile_action_secret_chat)).assertDoesNotExist()
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
