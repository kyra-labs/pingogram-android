package dev.kyralabs.pingogram.feature.profile.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatInfoScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatMembersScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatPermissionsScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.EditChatInfoScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.EditInviteLinkScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.InviteLinksScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.MemberRightsScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.preview.ChatInfoPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.DiscussionPickerContent
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every control of the profile, the group and channel info and their pages has a name and a 48 dp touch target
 * ([assertAccessible], `docs/audit/accessibility.md`), on a compact phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProfileAccessibilityChecksTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val mediaActions = SharedMediaActions({ _, _ -> }, { _, _ -> }, {})

    @Test
    fun profile() =
        check {
            ProfileScreen(
                state = ProfilePreviewData.content,
                media = ProfilePreviewData.media,
                onEvent = {},
                onMediaEvent = {},
                onBack = {},
                mediaActions = mediaActions,
                onEditContact = {},
            )
        }

    @Test
    fun chatInfo() =
        check {
            ChatInfoScreen(
                state = ChatInfoPreviewData.group,
                media = ProfilePreviewData.media,
                onEvent = {},
                onMediaEvent = {},
                onBack = {},
                mediaActions = mediaActions,
            )
        }

    @Test
    fun members() = check { ChatMembersScreen(ChatInfoPreviewData.membersPage, {}, {}) }

    @Test
    fun memberRights() = check { MemberRightsScreen(ChatInfoPreviewData.adminRights, {}, {}) }

    @Test
    fun chatSettings() = check { ChatSettingsScreen(ChatSettingsPreviewData.channel, {}, {}) }

    @Test
    fun basicGroupSettings() = check { ChatSettingsScreen(ChatSettingsPreviewData.basicGroup, {}, {}) }

    @Test
    fun discussionPicker() = check { DiscussionPickerContent(ChatSettingsPreviewData.pickerGroups, {}) }

    @Test
    fun permissions() = check { ChatPermissionsScreen(ChatInfoPreviewData.permissions, {}, {}) }

    @Test
    fun editInfo() = check { EditChatInfoScreen(ChatInfoPreviewData.editInfo, {}, {}) }

    @Test
    fun inviteLinks() = check { InviteLinksScreen(ChatInfoPreviewData.inviteLinks, {}, {}) }

    @Test
    fun newLink() = check { EditInviteLinkScreen(ChatInfoPreviewData.newLink, {}, {}) }

    private fun check(content: @Composable () -> Unit) {
        composeRule.setContent { PingogramTheme(dynamicColor = false) { content() } }
        composeRule.assertAccessible()
    }
}
