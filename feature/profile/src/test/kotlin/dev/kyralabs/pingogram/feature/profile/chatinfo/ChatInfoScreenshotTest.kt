package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.profile.chatinfo.preview.ChatInfoPreviewData
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.EXPANDED
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaActions
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaUiState
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the info page of groups and channels and the pages it opens (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md`
 * §12): a group the user administers in light, dark, AMOLED, high contrast and right to left, on an expanded window
 * with the shared media beside it, a channel the user reads, the error; the members, a member's rights and
 * restrictions, the permissions with slow mode, editing the photo and name (a new photo uploading in dark), the invite
 * links and a new link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatInfoScreenshotTest {
    @Test
    fun groupLight() = info(ScreenshotVariant.Light)

    @Test
    fun groupDark() = info(ScreenshotVariant.Dark)

    @Test
    fun groupAmoled() = info(ScreenshotVariant.Amoled)

    @Test
    fun groupHighContrast() = info(ScreenshotVariant.HighContrast)

    @Test
    fun groupRtl() = info(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = EXPANDED)
    fun groupExpanded() = info(ScreenshotVariant.Light)

    @Test
    fun channelDark() = info(ScreenshotVariant.Dark, ChatInfoPreviewData.channel, ProfilePreviewData.emptyMedia)

    @Test
    fun error() = info(ScreenshotVariant.Light, ChatInfoUiState.Error(DomainError.NotFound))

    @Test
    fun membersLight() = page(ScreenshotVariant.Light) { ChatMembersScreen(ChatInfoPreviewData.membersPage, {}, {}) }

    @Test
    fun membersDark() = page(ScreenshotVariant.Dark) { ChatMembersScreen(ChatInfoPreviewData.membersPage, {}, {}) }

    @Test
    fun membersEmpty() = page(ScreenshotVariant.Light) { ChatMembersScreen(ChatInfoPreviewData.membersEmpty, {}, {}) }

    @Test
    fun adminRights() = page(ScreenshotVariant.Light) { MemberRightsScreen(ChatInfoPreviewData.adminRights, {}, {}) }

    @Test
    fun restrictionsDark() =
        page(ScreenshotVariant.Dark) { MemberRightsScreen(ChatInfoPreviewData.restrictions, {}, {}) }

    @Test
    fun permissions() = page(ScreenshotVariant.Light) { ChatPermissionsScreen(ChatInfoPreviewData.permissions, {}, {}) }

    @Test
    fun editInfo() = page(ScreenshotVariant.Light) { EditChatInfoScreen(ChatInfoPreviewData.editInfo, {}, {}) }

    @Test
    fun editInfoUploadingDark() =
        page(ScreenshotVariant.Dark) { EditChatInfoScreen(ChatInfoPreviewData.editInfoUploading, {}, {}) }

    @Test
    fun inviteLinksLight() =
        page(ScreenshotVariant.Light) { InviteLinksScreen(ChatInfoPreviewData.inviteLinks, {}, {}) }

    @Test
    fun inviteLinksDark() = page(ScreenshotVariant.Dark) { InviteLinksScreen(ChatInfoPreviewData.inviteLinks, {}, {}) }

    @Test
    fun newLink() = page(ScreenshotVariant.Light) { EditInviteLinkScreen(ChatInfoPreviewData.newLink, {}, {}) }

    private fun info(
        variant: ScreenshotVariant,
        state: ChatInfoUiState = ChatInfoPreviewData.group,
        media: SharedMediaUiState = ProfilePreviewData.media,
    ) = captureRoboImage {
        ScreenshotTheme(variant) {
            ChatInfoScreen(
                state = state,
                media = media,
                onEvent = {},
                onMediaEvent = {},
                onBack = {},
                mediaActions = SharedMediaActions({ _, _ -> }, { _, _ -> }, {}),
            )
        }
    }

    private fun page(
        variant: ScreenshotVariant,
        content: @Composable () -> Unit,
    ) = captureRoboImage { ScreenshotTheme(variant, content) }
}
