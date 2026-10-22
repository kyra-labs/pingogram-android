package dev.kyralabs.pingogram.feature.profile.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The profile's confirmations (`docs/DESIGN_SYSTEM.md` §6 "Dialogs": `AlertDialog`s): starting a secret chat, which
 * says that it lives on this device and the other person's only and is gone after logging out, with the lock icon; and
 * blocking the user, destructive, its confirm button in `error`. "Cancel" closes either.
 */
@Composable
internal fun ProfileDialogs(
    dialog: ProfileDialog?,
    onEvent: (ProfileEvent) -> Unit,
) {
    when (dialog) {
        ProfileDialog.SecretChat -> {
            ConfirmDialog(
                icon = UiR.drawable.ic_lock,
                title = stringResource(R.string.profile_secret_chat_title),
                text = stringResource(R.string.profile_secret_chat_text),
                confirm = stringResource(R.string.profile_secret_chat_confirm),
                destructive = false,
                onConfirm = { onEvent(ProfileEvent.ConfirmSecretChat) },
                onDismiss = { onEvent(ProfileEvent.DismissDialog) },
            )
        }

        is ProfileDialog.Block -> {
            ConfirmDialog(
                icon = null,
                // The name, isolated: the title keeps its language's direction around it.
                title =
                    stringResource(
                        R.string.profile_block_title,
                        dialog.name.resolve().bidiIsolated(),
                    ).bidiIsolated(),
                text = stringResource(R.string.profile_block_text),
                confirm = stringResource(R.string.profile_block_confirm),
                destructive = true,
                onConfirm = { onEvent(ProfileEvent.ConfirmBlock) },
                onDismiss = { onEvent(ProfileEvent.DismissDialog) },
            )
        }

        null -> {}
    }
}

@Suppress("LongParameterList") // What a confirmation says and does.
@Composable
private fun ConfirmDialog(
    icon: Int?,
    title: String,
    text: String,
    confirm: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors =
                    if (destructive) {
                        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
            ) {
                Text(confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.profile_dialog_dismiss))
            }
        },
        icon = icon?.let { { Icon(painter = painterResource(it), contentDescription = null) } },
        title = { Text(title) },
        text = { Text(text) },
    )
}

/**
 * Reporting the account (`REQUIREMENTS.md` FR-P2; `docs/DESIGN_SYSTEM.md` §6 "Report sheet"), as the conversation
 * reports messages: a [ModalBottomSheet] that shows the step of TDLib's flow ([ProfileReportSheet.step]): "Report" with
 * a wavy progress indicator until the reasons come, then Telegram's question with its reasons as transparent list
 * items, or a comment in the rounded tonal text field with "Send report", enabled once there is a comment unless it is
 * optional. Steps crossfade on the effects springs while the sheet resizes on the default spatial spring; while a
 * report is on its way the progress indicator shows and the choices are disabled. Dragging it down, the scrim and
 * predictive back close it ([ProfileEvent.DismissReport]). It scrolls where the window is too short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportProfileSheet(
    sheet: ProfileReportSheet,
    comment: TextFieldState,
    onEvent: (ProfileEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    val motion = MaterialTheme.motionScheme
    ModalBottomSheet(onDismissRequest = { currentOnEvent(ProfileEvent.DismissReport) }, sheetState = sheetState) {
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
                    null -> ReportTitle(stringResource(R.string.profile_report_title))
                    is ProfileReportStep.Options -> OptionsStep(step, sheet.isReporting, currentOnEvent)
                    is ProfileReportStep.Comment -> CommentStep(step, comment, sheet.isReporting, currentOnEvent)
                }
                if (sheet.isReporting) {
                    LinearWavyProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = MaterialTheme.spacing.large,
                                    vertical = MaterialTheme.spacing.small,
                                ),
                    )
                }
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
    step: ProfileReportStep.Options,
    isReporting: Boolean,
    onEvent: (ProfileEvent) -> Unit,
) {
    ReportTitle(step.title.ifBlank { stringResource(R.string.profile_report_title) })
    step.options.forEachIndexed { index, option ->
        ListItem(
            onClick = { onEvent(ProfileEvent.ChooseReportOption(index)) },
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
    step: ProfileReportStep.Comment,
    comment: TextFieldState,
    isReporting: Boolean,
    onEvent: (ProfileEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val hasText by remember(comment) { derivedStateOf { comment.text.isNotBlank() } }
    ReportTitle(stringResource(R.string.profile_report_comment_title))
    Text(
        text = stringResource(R.string.profile_report_comment_body),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = spacing.large),
    )
    TextField(
        state = comment,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.medium),
        enabled = !isReporting,
        label = {
            val optional = step.isOptional
            Text(stringResource(if (optional) R.string.profile_report_comment_label_optional else COMMENT_LABEL))
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = COMMENT_MAX_LINES),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
    Button(
        onClick = { onEvent(ProfileEvent.SendReportComment) },
        enabled = !isReporting && (step.isOptional || hasText),
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.align(Alignment.End).padding(horizontal = spacing.large),
    ) {
        Text(stringResource(R.string.profile_report_send), style = MaterialTheme.typography.labelLargeEmphasized)
    }
}

/** The comment's label where Telegram needs one. */
private val COMMENT_LABEL = R.string.profile_report_comment_label

/** Lines the comment grows to before it scrolls. */
private const val COMMENT_MAX_LINES = 5

@PingogramPreviews
@Composable
private fun ReportProfileSheetPreview() {
    PingogramPreview {
        ReportProfileSheet(
            ProfileReportSheet(
                ProfileReportStep.Options("Why are you reporting this account?", persistentListOf("Spam", "Violence")),
            ),
            TextFieldState(),
            onEvent = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun SecretChatDialogPreview() {
    PingogramPreview { ProfileDialogs(ProfileDialog.SecretChat, onEvent = {}) }
}
