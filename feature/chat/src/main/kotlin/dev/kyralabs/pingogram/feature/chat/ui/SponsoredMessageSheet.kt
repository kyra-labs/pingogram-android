package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "About this ad" of a sponsored message (`REQUIREMENTS.md` §2; `docs/DESIGN_SYSTEM.md` §6 "Sponsored message"): a
 * [ModalBottomSheet] that says what sponsored messages are, who placed this one and what more Telegram tells about
 * it, with "Report ad" where it can be reported. A note says that Telegram Premium subscribers can turn ads off:
 * information only, never an offer or a link.
 *
 * - **Report** ([SponsoredEvent.Report]): when Telegram asks why, the sheet shows its reasons
 *   ([SponsoredSheet.report]), crossfading on the motion scheme's default effects spring while the sheet resizes on
 *   its default spatial spring; choosing one reports again with it ([SponsoredEvent.ChooseReportOption]), and the back
 *   button returns to the information ([SponsoredEvent.BackToInfo]). A wavy progress indicator shows while a report
 *   is on its way, the reasons disabled meanwhile. The view model closes the sheet once the report is done.
 * - Dragging it down, tapping the scrim and the back gesture (Material's predictive back) close it
 *   ([SponsoredEvent.DismissInfo]). It scrolls where the window is too short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SponsoredMessageSheet(
    sheet: SponsoredSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    val motion = MaterialTheme.motionScheme
    ModalBottomSheet(
        onDismissRequest = { currentOnEvent(SponsoredEvent.DismissInfo) },
        sheetState = sheetState,
    ) {
        AnimatedContent(
            targetState = sheet.report,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            contentKey = { it != null },
            label = "sponsoredReport",
        ) { report ->
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = MaterialTheme.spacing.large),
            ) {
                if (report != null) {
                    ReportStep(report, sheet.isReporting, currentOnEvent)
                } else {
                    AboutAd(sheet, currentOnEvent)
                }
            }
        }
    }
}

/** What sponsored messages are, the advertiser, more about the ad, "Report ad" and the note on Telegram Premium. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutAd(
    sheet: SponsoredSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    Text(
        text =
            stringResource(if (sheet.isRecommended) R.string.sponsored_about_recommended else R.string.sponsored_about),
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier = Modifier.padding(horizontal = spacing.large).semantics { heading() },
    )
    Text(
        text = stringResource(R.string.sponsored_about_text),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.small),
    )
    // Legal texts: people copy them.
    SelectionContainer {
        Column {
            if (sheet.sponsorInfo.isNotBlank()) {
                ListItem(
                    overlineContent = { Text(stringResource(R.string.sponsored_advertiser)) },
                    colors = transparent,
                ) {
                    Text(sheet.sponsorInfo)
                }
            }
            if (sheet.additionalInfo.isNotBlank()) {
                ListItem(
                    overlineContent = { Text(stringResource(R.string.sponsored_additional_info)) },
                    colors = transparent,
                ) {
                    Text(sheet.additionalInfo)
                }
            }
        }
    }
    if (sheet.canBeReported) {
        ListItem(
            onClick = { onEvent(SponsoredEvent.Report) },
            enabled = !sheet.isReporting,
            leadingContent = { Icon(painterResource(UiR.drawable.ic_error), contentDescription = null) },
            colors =
                ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = colors.error,
                    leadingContentColor = colors.error,
                ),
        ) {
            Text(stringResource(R.string.sponsored_report))
        }
    }
    if (sheet.isReporting) ReportProgress()
    Text(
        text = stringResource(R.string.sponsored_premium_note),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.small),
    )
}

/** Why the ad is reported: the back button, Telegram's question and its reasons, disabled while [isReporting]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReportStep(
    report: SponsoredReportStep,
    isReporting: Boolean,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(SponsoredEvent.BackToInfo) }) {
            Icon(
                painter = painterResource(UiR.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.sponsored_report_back),
            )
        }
        Text(
            text = report.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
    if (isReporting) ReportProgress()
    report.options.forEachIndexed { index, option ->
        ListItem(
            onClick = { onEvent(SponsoredEvent.ChooseReportOption(index)) },
            enabled = !isReporting,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(option)
        }
    }
}

/** The report on its way. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReportProgress() {
    val spacing = MaterialTheme.spacing
    LinearWavyProgressIndicator(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.small),
    )
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
