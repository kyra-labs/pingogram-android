package dev.kyralabs.pingogram.feature.feedback

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import dev.kyralabs.pingogram.core.domain.model.DiagnosticReport
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "Send feedback" (`docs/TESTING_GUIDE.md`), pushed above Settings → About: a small top bar with back on
 * `surfaceContainer`, and on the rounded `surface` sheet below, at most `contentMaxWidth` wide inside the window
 * margin:
 * - what the page does, then "What happened?" in Material's rounded tonal field;
 * - "In the report": the app, the device, the last crash and the log's size on a `surfaceContainerHigh` card, what is
 *   taken out of them, "Show report" (the whole text, selectable, in `FontFamily.Monospace` on
 *   `surfaceContainerHighest`, growing in on `defaultSpatialSpec`), "Copy" and "Delete crash report" in `error` where a
 *   crash is kept;
 * - "Open GitHub issue" (tonal) and "Share report" (filled) at the end, every button with the press morph.
 *
 * Nothing leaves the device until the user shares the report or submits the issue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedbackScreen(
    state: FeedbackUiState,
    onEvent: (FeedbackEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        // The top bar's container; the page sits on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.feedback_title),
                        modifier = Modifier.semantics { heading() },
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.feedback_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            when (state) {
                FeedbackUiState.Loading -> {
                    DelayedLoadingIndicator(Modifier.align(Alignment.Center).padding(contentPadding))
                }

                is FeedbackUiState.Content -> {
                    FeedbackContent(state, onEvent, Modifier.padding(contentPadding))
                }
            }
        }
    }
}

@Composable
private fun FeedbackContent(
    state: FeedbackUiState.Content,
    onEvent: (FeedbackEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = windowMargin(), vertical = MaterialTheme.spacing.large)
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.feedback_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextField(
                value = state.description,
                onValueChange = { onEvent(FeedbackEvent.DescriptionChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.userContent,
                label = { Text(stringResource(R.string.feedback_description)) },
                placeholder = { Text(stringResource(R.string.feedback_description_hint)) },
                minLines = DESCRIPTION_LINES,
                shape = TextFieldDefaults.roundedShape,
                colors = TextFieldDefaults.tonalColors(),
            )
            Text(
                text = stringResource(R.string.feedback_section_report),
                modifier = Modifier.padding(top = MaterialTheme.spacing.small).semantics { heading() },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            ReportSummary(state.report)
            Text(
                text = stringResource(R.string.feedback_redacted),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            ReportText(state, onEvent)
            Actions(onEvent)
            Text(
                text = stringResource(R.string.feedback_issue_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** The app, the device, the last crash and the log, each a label over its value, on one card. */
@Composable
private fun ReportSummary(report: DiagnosticReport) {
    val crash = report.crash
    val crashTime = crash?.let { rememberDateTime(it.timeMillis) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            SummaryLine(stringResource(R.string.feedback_app), report.app)
            SummaryLine(stringResource(R.string.feedback_device), report.device)
            SummaryLine(
                stringResource(R.string.feedback_crash),
                if (crash == null || crashTime == null) {
                    stringResource(R.string.feedback_no_crash)
                } else {
                    stringResource(R.string.feedback_crash_time, crashTime, crash.thread) + "\n" +
                        crash.trace.lineSequence().first()
                },
            )
            SummaryLine(
                stringResource(R.string.feedback_log),
                if (report.log.isEmpty()) {
                    stringResource(R.string.feedback_log_empty)
                } else {
                    pluralStringResource(R.plurals.feedback_log_lines, report.log.size, report.log.size)
                },
            )
        }
    }
}

/** One fact of the report: its [label] over its [value], read as one by screen readers. */
@Composable
private fun SummaryLine(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium.userContent)
    }
}

/**
 * "Show report" / "Hide report", "Copy" and, where a crash is kept, "Delete crash report", then the report while
 * shown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportText(
    state: FeedbackUiState.Content,
    onEvent: (FeedbackEvent) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            TextButton(onClick = { onEvent(FeedbackEvent.ToggleReport) }, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(
                        if (state.showReport) R.string.feedback_hide_report else R.string.feedback_show_report,
                    ),
                )
            }
            TextButton(onClick = { onEvent(FeedbackEvent.Copy) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.feedback_copy))
            }
            if (state.report.crash != null) {
                TextButton(
                    onClick = { onEvent(FeedbackEvent.DeleteCrash) },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.feedback_delete_crash))
                }
            }
        }
        AnimatedVisibility(
            visible = state.showReport,
            enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = state.reportText,
                        modifier = Modifier.padding(MaterialTheme.spacing.medium),
                        style =
                            MaterialTheme.typography.bodySmall
                                .copy(fontFamily = FontFamily.Monospace)
                                .userContent,
                    )
                }
            }
        }
    }
}

/** "Open GitHub issue" and "Share report" at the end, the filled one last. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(onEvent: (FeedbackEvent) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        FilledTonalButton(onClick = { onEvent(FeedbackEvent.OpenIssue) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.feedback_open_issue))
        }
        Button(onClick = { onEvent(FeedbackEvent.Share) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.feedback_share), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/**
 * [millis] as the user's language writes a date and time ("Oct 22, 2026, 12:00 PM"), in the device's time zone and
 * 12- or 24-hour clock. The pattern is Android's (`DateFormat.getBestDateTimePattern`), as the conversation's times
 * are, not the JDK's, whose locale data differs between versions.
 */
@Composable
private fun rememberDateTime(millis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(millis, locale, is24Hour) {
        val pattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "yMMMdHm" else "yMMMdhm")
        val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern(pattern, locale).format(time)
    }
}

/** The description field's height at rest, so there is room to describe what happened. */
private const val DESCRIPTION_LINES = 3
