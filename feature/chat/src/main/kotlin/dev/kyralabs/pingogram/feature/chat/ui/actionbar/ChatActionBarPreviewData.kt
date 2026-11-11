package dev.kyralabs.pingogram.feature.chat.ui.actionbar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.domain.model.AccountInfo
import dev.kyralabs.pingogram.core.domain.model.ChatActionBar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

/** The action bars the previews and goldens show. */
internal object ChatActionBarPreviewData {
    /** Someone new wrote first: March 2024, a German number, a name changed on Oct 3, 2026. */
    val reportAddBlock =
        ActionBarUi(
            ChatActionBar.ReportAddBlock(
                canUnarchive = false,
                account = AccountInfo(3, 2024, "DE", lastNameChangeDate = 1_759_500_000L),
            ),
            name = "Ada",
        )

    val sharePhoneNumber = ActionBarUi(ChatActionBar.SharePhoneNumber, name = "Ada")

    val joinRequest =
        ActionBarUi(ChatActionBar.JoinRequest("Rust Berlin", isChannel = false, requestDate = 1_761_100_000L), "Ada")

    val reportSpam = ActionBarUi(ChatActionBar.ReportSpam(canUnarchive = true), name = null)
}

@PingogramPreviews
@Composable
private fun ChatActionBarReportAddBlockPreview() {
    PingogramPreview { ActionBarSlot(ChatActionBarPreviewData.reportAddBlock, onEvent = {}, PaddingValues()) }
}

@PingogramPreviews
@Composable
private fun ChatActionBarSharePhonePreview() {
    PingogramPreview { ActionBarSlot(ChatActionBarPreviewData.sharePhoneNumber, onEvent = {}, PaddingValues()) }
}

@PingogramPreviews
@Composable
private fun ChatActionBarJoinRequestPreview() {
    PingogramPreview { ActionBarSlot(ChatActionBarPreviewData.joinRequest, onEvent = {}, PaddingValues()) }
}

@PingogramPreviews
@Composable
private fun BlockAlertPreview() {
    PingogramPreview { BlockAlert(name = "Ada", onEvent = {}) }
}
