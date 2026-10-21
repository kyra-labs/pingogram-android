package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SelectionPreviewData

/**
 * Reporting messages to Telegram's moderators (`REQUIREMENTS.md` FR-C6 "report"): a [ModalBottomSheet] that shows the
 * step of TDLib's flow ([ReportSheet.step]): "Report" with a wavy progress indicator until the reasons come, then
 * Telegram's question with its reasons as list items ([SelectionEvent.ChooseReportOption]), or a comment in the rounded
 * tonal text field (`docs/DESIGN_SYSTEM.md` §6 "Text fields") with "Send report" ([SelectionEvent.SendReportComment]),
 * enabled once there is a comment unless it is optional. Each step crossfades on the motion scheme's default effects
 * spring while the sheet resizes on its default spatial spring; while a report is on its way the progress indicator
 * shows and the choices are disabled. The view model closes it once the report is done.
 *
 * Dragging it down, tapping the scrim and the back gesture (Material's predictive back) close it
 * ([SelectionEvent.DismissReport]). It scrolls where the window is too short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportMessagesSheet(
    sheet: ReportSheet,
    comment: TextFieldState,
    onEvent: (ChatEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    val motion = MaterialTheme.motionScheme
    ModalBottomSheet(
        onDismissRequest = { currentOnEvent(SelectionEvent.DismissReport) },
        sheetState = sheetState,
    ) {
        AnimatedContent(
            targetState = sheet.step,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            contentKey = { it?.javaClass },
            label = "reportStep",
        ) { step ->
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = MaterialTheme.spacing.large),
            ) {
                when (step) {
                    null -> ReportTitle(stringResource(R.string.report_title))
                    is ReportStep.Options -> OptionsStep(step, sheet.isReporting, currentOnEvent)
                    is ReportStep.Comment -> CommentStep(step, comment, sheet.isReporting, currentOnEvent)
                }
                if (sheet.isReporting) ReportProgress()
            }
        }
    }
}

/** The step's title, a heading in `titleLargeEmphasized`. */
@Composable
private fun ReportTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier =
            Modifier
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small)
                .semantics { heading() },
    )
}

/** Telegram's question and its reasons, disabled while [isReporting]. */
@Composable
private fun OptionsStep(
    step: ReportStep.Options,
    isReporting: Boolean,
    onEvent: (ChatEvent) -> Unit,
) {
    ReportTitle(step.title.ifBlank { stringResource(R.string.report_title) })
    step.options.forEachIndexed { index, option ->
        ListItem(
            onClick = { onEvent(SelectionEvent.ChooseReportOption(index)) },
            enabled = !isReporting,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Text(option)
        }
    }
}

/** The comment and "Send report", enabled once there is a comment unless it is optional, and not while reporting. */
@Composable
private fun ColumnScope.CommentStep(
    step: ReportStep.Comment,
    comment: TextFieldState,
    isReporting: Boolean,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val hasText by remember(comment) { derivedStateOf { comment.text.isNotBlank() } }
    ReportTitle(stringResource(R.string.report_comment_title))
    Text(
        text = stringResource(R.string.report_comment_body),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = spacing.large),
    )
    TextField(
        state = comment,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.medium),
        enabled = !isReporting,
        label = {
            Text(
                stringResource(
                    if (step.isOptional) R.string.report_comment_label_optional else R.string.report_comment_label,
                ),
            )
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = COMMENT_MAX_LINES),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
    Button(
        onClick = { onEvent(SelectionEvent.SendReportComment) },
        enabled = !isReporting && (step.isOptional || hasText),
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.align(Alignment.End).padding(horizontal = spacing.large),
    ) {
        Text(stringResource(R.string.report_send), style = MaterialTheme.typography.labelLargeEmphasized)
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

/** Lines the comment grows to before it scrolls. */
private const val COMMENT_MAX_LINES = 5

@PingogramPreviews
@Composable
private fun ReportMessagesSheetLoadingPreview() {
    PingogramPreview { ReportMessagesSheet(ReportSheet(isReporting = true), TextFieldState(), onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun ReportMessagesSheetOptionsPreview() {
    PingogramPreview { ReportMessagesSheet(SelectionPreviewData.reportOptions, TextFieldState(), onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun ReportMessagesSheetCommentPreview() {
    PingogramPreview {
        ReportMessagesSheet(
            ReportSheet(ReportStep.Comment(isOptional = false)),
            TextFieldState("Spam links"),
            onEvent = {},
        )
    }
}
