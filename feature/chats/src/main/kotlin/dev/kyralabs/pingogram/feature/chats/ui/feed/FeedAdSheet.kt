package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import dev.kyralabs.pingogram.core.ui.component.SponsoredMessageSheet
import dev.kyralabs.pingogram.core.ui.component.SponsoredSheet
import dev.kyralabs.pingogram.core.ui.component.SponsoredSheetActions

/** "About this ad" of a feed ad: the conversation's sheet (`core:ui`), its actions sent as [FeedAdEvent]s. */
@Composable
internal fun FeedAdSheet(
    sheet: SponsoredSheet,
    onEvent: (FeedEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val actions =
        remember {
            SponsoredSheetActions(
                onDismiss = { currentOnEvent(FeedAdEvent.DismissInfo) },
                onReport = { currentOnEvent(FeedAdEvent.Report) },
                onChooseOption = { currentOnEvent(FeedAdEvent.ChooseReportOption(it)) },
                onBack = { currentOnEvent(FeedAdEvent.BackToInfo) },
            )
        }
    SponsoredMessageSheet(sheet, actions)
}
