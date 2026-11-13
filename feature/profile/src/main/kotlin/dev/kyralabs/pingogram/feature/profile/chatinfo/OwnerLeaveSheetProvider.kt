package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.profile.chatinfo.preview.ChatInfoPreviewData

/**
 * The owner's sheet before leaving: choosing, the password, a wrong one, transferring, and a basic group without
 * administrators.
 */
internal class OwnerLeaveSheetProvider : PreviewParameterProvider<OwnerLeaveSheet> {
    override val values =
        sequenceOf(
            ChatInfoPreviewData.ownerLeave,
            ChatInfoPreviewData.ownerLeavePassword,
            ChatInfoPreviewData.ownerLeaveWrongPassword,
            ChatInfoPreviewData.ownerLeavePassword.copy(isBusy = true),
            ChatInfoPreviewData.ownerLeaveNoAdmins,
        )
}

@PingogramPreviews
@Composable
private fun OwnerLeavePreview(
    @PreviewParameter(OwnerLeaveSheetProvider::class) sheet: OwnerLeaveSheet,
) {
    PingogramPreview {
        OwnerLeaveContent(sheet, "Rust Berlin", remember { TextFieldState("hunter2") }, onEvent = {})
    }
}
