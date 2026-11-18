package dev.kyralabs.pingogram.core.ui.component

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * "About this ad" for sponsored message [messageId] while it is open: who placed it ([sponsorInfo]) and more about it
 * ([additionalInfo]), each empty if TDLib has nothing, and the report entry if [canBeReported]. While a report asks
 * why, [report] is the step that shows its options; [isReporting] while a report is on its way.
 */
@Immutable
data class SponsoredSheet(
    val messageId: Long,
    val isRecommended: Boolean,
    val sponsorInfo: String,
    val additionalInfo: String,
    val canBeReported: Boolean,
    val report: SponsoredReportStep? = null,
    val isReporting: Boolean = false,
) {
    override fun toString() = "SponsoredSheet(messageId=$messageId, report=${report != null}, …)"
}

/** A report that asks why: [title] asks, and the user picks one of [options]. */
@Immutable
data class SponsoredReportStep(
    val title: String,
    val options: ImmutableList<String>,
)

/**
 * What [SponsoredMessageSheet] does: close ([onDismiss]), report the ad ([onReport]), report it again with the
 * option at an index of the report step ([onChooseOption]), and go back from that step to the ad's information
 * ([onBack]).
 */
class SponsoredSheetActions(
    val onDismiss: () -> Unit,
    val onReport: () -> Unit,
    val onChooseOption: (Int) -> Unit,
    val onBack: () -> Unit,
)

/**
 * "About this ad" of a sponsored message (`REQUIREMENTS.md` §2; `docs/DESIGN_SYSTEM.md` §6 "Sponsored message"), shared
 * by the conversation and the Channel feed: a [PingogramBottomSheet] that says what sponsored messages are, who placed
 * this one and what more Telegram tells about it, with "Report ad" where it can be reported. A note says that Telegram
 * Premium subscribers can turn ads off: information only, never an offer or a link.
 *
 * - **Report** ([SponsoredSheetActions.onReport]): when Telegram asks why, the sheet shows its reasons
 *   ([SponsoredSheet.report]), crossfading on the motion scheme's default effects spring while the sheet resizes on
 *   its default spatial spring; choosing one reports again with it, and the back button returns to the information.
 *   A wavy progress indicator shows while a report is on its way, the reasons disabled meanwhile. Whoever shows the
 *   sheet closes it once the report is done.
 * - Dragging it down, tapping the scrim and the back gesture (Material's predictive back) close it
 *   ([SponsoredSheetActions.onDismiss]). It scrolls where the window is too short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsoredMessageSheet(
    sheet: SponsoredSheet,
    actions: SponsoredSheetActions,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val motion = MaterialTheme.motionScheme
    PingogramBottomSheet(onDismissRequest = actions.onDismiss, sheetState = sheetState) {
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
                    ReportStep(report, sheet.isReporting, actions)
                } else {
                    AboutAd(sheet, actions)
                }
            }
        }
    }
}

/** What sponsored messages are, the advertiser, more about the ad, "Report ad" and the note on Telegram Premium. */
@Composable
private fun AboutAd(
    sheet: SponsoredSheet,
    actions: SponsoredSheetActions,
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
            onClick = actions.onReport,
            enabled = !sheet.isReporting,
            leadingContent = { Icon(painterResource(R.drawable.ic_error), contentDescription = null) },
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
@Composable
private fun ReportStep(
    report: SponsoredReportStep,
    isReporting: Boolean,
    actions: SponsoredSheetActions,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
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
            onClick = { actions.onChooseOption(index) },
            enabled = !isReporting,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(option)
        }
    }
}

/** The report on its way. */
@Composable
private fun ReportProgress() {
    val spacing = MaterialTheme.spacing
    LinearWavyProgressIndicator(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.small),
    )
}

private val previewActions = SponsoredSheetActions({}, {}, {}, {})

@PingogramPreviews
@Composable
private fun SponsoredMessageSheetInfoPreview() {
    PingogramPreview {
        SponsoredMessageSheet(
            SponsoredSheet(
                1,
                isRecommended = false,
                "Trip Planner Ltd.",
                "Shown to people who follow travel channels.",
                true,
            ),
            previewActions,
        )
    }
}

@PingogramPreviews
@Composable
private fun SponsoredMessageSheetReportPreview() {
    PingogramPreview {
        SponsoredMessageSheet(
            SponsoredSheet(
                1,
                isRecommended = false,
                sponsorInfo = "",
                additionalInfo = "",
                canBeReported = true,
                report =
                    SponsoredReportStep(
                        "Why are you reporting this ad?",
                        persistentListOf("Not relevant", "Misleading"),
                    ),
            ),
            previewActions,
        )
    }
}
