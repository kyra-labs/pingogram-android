package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.preview.ChatInfoPreviewData
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaActions
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The info page, the rights page and the link editor send their events: the info page's pills and rows, a right's
 * switch (and none for a right the user lacks), creating a link with a name, an expiry and join requests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatInfoPagesTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent { PingogramTheme(dynamicColor = false, content = content) }
    }

    private fun string(
        @StringRes id: Int,
    ) = composeRule.activity.getString(id)

    @Test
    fun `the info page leaves, opens all members and the administrators' pages`() {
        val events = mutableListOf<ChatInfoEvent>()
        setContent {
            ChatInfoScreen(
                state = ChatInfoPreviewData.group,
                media = ProfilePreviewData.emptyMedia,
                onEvent = { events += it },
                onMediaEvent = {},
                onBack = {},
                mediaActions = SharedMediaActions({ _, _ -> }, { _, _ -> }, {}),
            )
        }

        composeRule.onNodeWithText(string(R.string.chat_info_action_leave)).performClick()
        val grid = composeRule.onNodeWithTag(CHAT_INFO_GRID_TEST_TAG)
        grid.performScrollToNode(hasText("See all 1,024 members"))
        composeRule.onNodeWithText("See all 1,024 members").performClick()
        grid.performScrollToNode(hasText(string(R.string.chat_info_invite_links)))
        composeRule.onNodeWithText(string(R.string.chat_info_invite_links)).performClick()

        assertEquals(
            listOf(
                ChatInfoEvent.Leave,
                ChatInfoEvent.OpenMembers(MemberFilter.Recent),
                ChatInfoEvent.Open(ChatInfoPage.InviteLinks),
            ),
            events,
        )
    }

    @Test
    fun `a right's switch toggles it, one the user lacks does not, and saving promotes`() {
        val events = mutableListOf<MemberRightsEvent>()
        setContent { MemberRightsScreen(ChatInfoPreviewData.adminRights, onEvent = { events += it }, onBack = {}) }

        composeRule.onNodeWithTag(AdminRight.DeleteMessages.key).performScrollTo().performClick()
        composeRule
            .onNodeWithTag(AdminRight.Anonymous.key)
            .performScrollTo()
            .assertIsNotEnabled()
            .performClick()
        composeRule
            .onNode(hasText(string(R.string.chat_info_member_promote)) and hasClickAction())
            .performScrollTo()
            .performClick()

        assertEquals(
            listOf(MemberRightsEvent.Toggle(AdminRight.DeleteMessages.key, on = false), MemberRightsEvent.Save),
            events,
        )
    }

    @Test
    fun `a new link takes a name, an expiry and join requests, then is created`() {
        val events = mutableListOf<EditInviteLinkEvent>()
        setContent {
            EditInviteLinkScreen(EditInviteLinkUiState.Content(isNew = true), onEvent = { events += it }, onBack = {})
        }

        composeRule.onNodeWithTag(LINK_NAME_TEST_TAG).performTextInput("Flyer")
        composeRule.onNodeWithText(string(R.string.chat_info_link_in_day)).performScrollTo().performClick()
        composeRule.onNodeWithTag(JOIN_REQUESTS_TEST_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.chat_info_link_create_button)).performScrollTo().performClick()

        assertTrue(events.toString(), EditInviteLinkEvent.NameChanged("Flyer") in events)
        assertEquals(
            listOf(
                EditInviteLinkEvent.SelectExpiry(ExpiryChoice.After(DAY)),
                EditInviteLinkEvent.SetJoinRequests(on = true),
                EditInviteLinkEvent.Save,
            ),
            events.filterNot { it is EditInviteLinkEvent.NameChanged },
        )
    }

    private companion object {
        const val DAY = 86_400L
    }
}
