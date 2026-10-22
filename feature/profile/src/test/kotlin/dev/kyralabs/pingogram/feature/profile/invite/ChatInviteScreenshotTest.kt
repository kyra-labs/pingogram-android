package dev.kyralabs.pingogram.feature.profile.invite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the join sheet's content (`docs/TESTING.md`), on the sheet's container as `ModalBottomSheet` draws it:
 * a group with members in light and dark, right to left and high contrast, a channel that takes requests, the request
 * sent, and an expired link.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatInviteScreenshotTest {
    @Test
    fun groupLight() = capture(ScreenshotVariant.Light, ChatInvitePreviewData.group)

    @Test
    fun groupDark() = capture(ScreenshotVariant.Dark, ChatInvitePreviewData.group)

    @Test
    fun groupRtl() = capture(ScreenshotVariant.Rtl, ChatInvitePreviewData.group)

    @Test
    fun groupHighContrast() = capture(ScreenshotVariant.HighContrast, ChatInvitePreviewData.group)

    @Test
    fun channelRequestDark() = capture(ScreenshotVariant.Dark, ChatInvitePreviewData.channelRequest)

    @Test
    fun joiningLight() = capture(ScreenshotVariant.Light, ChatInvitePreviewData.group.copy(isJoining = true))

    @Test
    fun requestSentLight() = capture(ScreenshotVariant.Light, ChatInviteUiState.RequestSent(GroupKind.Channel))

    @Test
    fun expiredDark() =
        capture(ScreenshotVariant.Dark, ChatInviteUiState.Failed(DomainError.InviteLinkInvalid, canRetry = false))

    private fun capture(
        variant: ScreenshotVariant,
        state: ChatInviteUiState,
    ) = captureRoboImage { ScreenshotTheme(variant) { SheetSurface { ChatInviteContent(state, {}, {}) } } }
}

/** [content] on a bottom sheet's container, its top corners and padding, as in the sheet's window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BottomSheetDefaults.ExpandedShape,
        color = BottomSheetDefaults.ContainerColor,
    ) {
        Box(Modifier.padding(top = MaterialTheme.spacing.extraLarge)) { content() }
    }
}
