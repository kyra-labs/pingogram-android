package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.CreateChatPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What picking people and choosing a channel's link send as the user taps and types (FR-P4). The screens are
 * stateless: these check their events, and what they show for each state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CreateChatScreensTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val memberEvents = mutableListOf<MemberPickerEvent>()
    private val typeEvents = mutableListOf<NewChannelTypeEvent>()
    private var actions = 0

    private fun setPicker(state: MemberPickerUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                MemberPickerScreen(
                    state = state,
                    title = "New group",
                    action = string(R.string.create_next),
                    onEvent = { memberEvents += it },
                    onAction = { actions++ },
                    onBack = {},
                )
            }
        }
    }

    // Picking

    @Test
    fun `a contact's row toggles it, picked ones read as checked`() {
        setPicker(CreateChatPreviewData.picking)

        composeRule.onNode(isToggleable() and hasText("Grace Hopper")).assertIsOn()
        composeRule.onNode(isToggleable() and hasText("Alan Turing")).assertIsOff().performClick()

        assertEquals(listOf(MemberPickerEvent.Toggle(11)), memberEvents)
    }

    @Test
    fun `a picked person's chip removes them`() {
        setPicker(CreateChatPreviewData.picking)

        composeRule.onNodeWithContentDescription(string(R.string.create_member_remove, "Ada Lovelace")).performClick()

        assertEquals(listOf(MemberPickerEvent.Remove(2)), memberEvents)
    }

    @Test
    fun `typing searches, and the action goes on`() {
        setPicker(CreateChatPreviewData.picking)

        composeRule.onNode(hasSetTextAction()).performTextInput("Gr")
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(string(R.string.create_next)).performClick()

        assertEquals(listOf(MemberPickerEvent.QueryChanged("Gr")), memberEvents)
        assertEquals(1, actions)
    }

    @Test
    fun `no matches, no contacts and a failure say so, and try again retries`() {
        var state by mutableStateOf(CreateChatPreviewData.noneMatching)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                MemberPickerScreen(state, "New group", "Next", { memberEvents += it }, {}, {})
            }
        }
        composeRule.onNodeWithText(string(R.string.create_no_results)).assertExists()

        state = CreateChatPreviewData.noContacts
        composeRule.onNodeWithText(string(R.string.create_no_contacts)).assertExists()

        state = CreateChatPreviewData.contactsError
        composeRule.onNodeWithText(string(R.string.search_retry)).performClick()
        assertEquals(listOf(MemberPickerEvent.Retry), memberEvents)
    }

    // The channel's link

    private fun setType(initial: NewChannelTypeUiState): (NewChannelTypeUiState) -> Unit {
        var state by mutableStateOf(initial)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                NewChannelTypeScreen(state, { typeEvents += it }, onCopyLink = {}, onShareLink = {})
            }
        }
        return { state = it }
    }

    @Test
    fun `choosing public sends it, and typing a username sends each change`() {
        val update = setType(CreateChatPreviewData.channelPrivate)

        composeRule.onNodeWithText(string(R.string.create_public)).performClick()
        assertEquals(listOf(NewChannelTypeEvent.TypeChanged(isPublic = true)), typeEvents)

        typeEvents.clear()
        update(CreateChatPreviewData.channelChecking.copy(username = "", usernameStatus = UsernameStatus.Empty))
        composeRule.onNode(hasSetTextAction()).performTextInput("news")
        assertEquals(listOf(NewChannelTypeEvent.UsernameChanged("news")), typeEvents)
    }

    @Test
    fun `the field says what Telegram answered, and next waits for a free username`() {
        val update = setType(CreateChatPreviewData.channelChecking)
        composeRule.onNodeWithText(string(R.string.create_link_checking)).assertExists()
        composeRule.onNodeWithText(string(R.string.create_next)).performClick()
        assertTrue(typeEvents.isEmpty())

        update(CreateChatPreviewData.channelTaken)
        composeRule.onNodeWithText(string(R.string.create_link_taken)).assertExists()

        update(CreateChatPreviewData.channelAvailable)
        composeRule.onNodeWithText(string(R.string.create_link_available, "pingogram_news")).assertExists()
        composeRule.onNodeWithText(string(R.string.create_next)).performClick()
        assertEquals(listOf(NewChannelTypeEvent.Continue), typeEvents)
    }

    @Test
    fun `a private channel shows its link with copy and share`() {
        var copied: String? = null
        var shared: String? = null
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                NewChannelTypeScreen(
                    state = CreateChatPreviewData.channelPrivate,
                    onEvent = {},
                    onCopyLink = { copied = it },
                    onShareLink = { shared = it },
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.create_link_copy)).performClick()
        composeRule.onNodeWithText(string(R.string.create_link_share)).performClick()

        assertEquals("https://t.me/+AbCdEf123456", copied)
        assertEquals("https://t.me/+AbCdEf123456", shared)
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)
}
