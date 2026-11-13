package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.AddChatsContent
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.AddChatsSheet
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.Membership
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.RenameCommunityContent
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a community's page (`docs/DESIGN_SYSTEM.md` §6 "Community page", the owner's board 19a): the owner's in
 * every look the audit asks for, at a 700 dp width and at 200% font, a member's, the
 * error, and the rename dialog's content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CommunityScreenshotTest {
    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = 2f)
    fun fontScale200() = capture(ScreenshotVariant.Light)

    /** A member's page; "Join" rather than its spinner, which animates. */
    @Test
    fun memberDark() =
        capture(
            ScreenshotVariant.Dark,
            CommunityPreviewData.member.copy(
                chats =
                    CommunityPreviewData.member.chats
                        .map {
                            if (it.membership ==
                                Membership.Joining
                            ) {
                                it.copy(membership = Membership.CanJoin)
                            } else {
                                it
                            }
                        }.toImmutableList(),
            ),
        )

    @Test
    fun error() = capture(ScreenshotVariant.Light, CommunityUiState.Error(DomainError.Network))

    @Test
    fun renameDialog() =
        captureRoboImage {
            ScreenshotTheme(ScreenshotVariant.Light) {
                // The dialog's content on its surface: a dialog window takes the focus on some runs and not others.
                Surface(
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    RenameCommunityContent("Kyra Labs Community", isBusy = false, onEvent = {})
                }
            }
        }

    @Test
    fun addChatsLight() = captureSheet(ScreenshotVariant.Light, CommunityPreviewData.addChats, isDirect = true)

    @Test
    fun addChatsDark() = captureSheet(ScreenshotVariant.Dark, CommunityPreviewData.addChats, isDirect = true)

    @Test
    fun suggestChatRtl() = captureSheet(ScreenshotVariant.Rtl, CommunityPreviewData.addChats, isDirect = false)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = 2f)
    fun addChatsFontScale200() = captureSheet(ScreenshotVariant.Light, CommunityPreviewData.addChats, isDirect = true)

    @Test
    fun addChatsEmpty() =
        captureSheet(ScreenshotVariant.Light, AddChatsSheet(chats = persistentListOf()), isDirect = true)

    /** The sheet's content on a sheet's surface: a sheet's window shows on some runs and not others. */
    private fun captureSheet(
        variant: ScreenshotVariant,
        sheet: AddChatsSheet,
        isDirect: Boolean,
    ) = captureRoboImage {
        ScreenshotTheme(
            variant,
        ) { SheetSurface { AddChatsContent(sheet, isDirect, onEvent = {}) } }
    }

    private fun capture(
        variant: ScreenshotVariant,
        state: CommunityUiState = CommunityPreviewData.owner,
    ) = captureRoboImage { ScreenshotTheme(variant) { CommunityScreen(state, onEvent = {}, onBack = {}) } }
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
