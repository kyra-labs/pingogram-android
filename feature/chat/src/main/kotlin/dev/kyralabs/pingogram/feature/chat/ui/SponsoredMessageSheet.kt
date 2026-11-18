package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.kyralabs.pingogram.core.ui.component.SponsoredSheet
import dev.kyralabs.pingogram.core.ui.component.SponsoredSheetActions
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import dev.kyralabs.pingogram.core.ui.component.SponsoredMessageSheet as SharedSponsoredMessageSheet

/**
 * The conversation's "About this ad": `core:ui`'s shared sheet ([SharedSponsoredMessageSheet]), its actions sent as
 * [SponsoredEvent]s to the view model, which closes it once a report is done.
 */
@Composable
internal fun SponsoredMessageSheet(
    sheet: SponsoredSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val actions =
        remember {
            SponsoredSheetActions(
                onDismiss = { currentOnEvent(SponsoredEvent.DismissInfo) },
                onReport = { currentOnEvent(SponsoredEvent.Report) },
                onChooseOption = { currentOnEvent(SponsoredEvent.ChooseReportOption(it)) },
                onBack = { currentOnEvent(SponsoredEvent.BackToInfo) },
            )
        }
    SharedSponsoredMessageSheet(sheet, actions)
}

@PingogramPreviews
@Composable
private fun SponsoredMessageSheetInfoPreview() {
    PingogramPreview { SponsoredMessageSheet(SponsoredPreviewData.info, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SponsoredMessageSheetReportPreview() {
    PingogramPreview { SponsoredMessageSheet(SponsoredPreviewData.reportStep, onEvent = {}) }
}
