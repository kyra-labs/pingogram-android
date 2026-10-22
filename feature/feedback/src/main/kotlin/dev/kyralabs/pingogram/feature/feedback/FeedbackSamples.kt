package dev.kyralabs.pingogram.feature.feedback

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.domain.model.CrashReport
import dev.kyralabs.pingogram.core.domain.model.DiagnosticReport
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import kotlinx.collections.immutable.persistentListOf

/** A report as a tester's phone would make it, for previews and goldens. */
internal object FeedbackSamples {
    val crash =
        CrashReport(
            timeMillis = 1_792_670_400_000,
            thread = "main",
            trace =
                "java.lang.IllegalStateException: Expected \"[text]\"\n" +
                    "\tat dev.kyralabs.pingogram.feature.chat.ChatViewModel.onEvent(ChatViewModel.kt:214)\n" +
                    "\tat dev.kyralabs.pingogram.feature.chat.ui.ComposerKt.Composer(Composer.kt:88)",
        )

    val report =
        DiagnosticReport(
            generatedAtMillis = 1_792_674_000_000,
            app = "Pingogram 0.5.0 (5000002), gms, release",
            device = "Google Pixel 9, Android 16 (API 36), arm64-v8a",
            crash = crash,
            log =
                persistentListOf(
                    "[ 1][t 4][1792673990.123456][Session.cpp:1512][#1][!Session]\t" +
                        "Failed to connect: [Error : 0 : Timeout]",
                    "[ 1][t 2][1792673995.654321][FileLoader.cpp:220][!FileLoader]\t" +
                        "File part 3 failed: text = [redacted]",
                ),
        )

    fun content(
        report: DiagnosticReport = this.report,
        description: String = "",
        showReport: Boolean = false,
    ) = FeedbackUiState.Content(report, description, showReport, report.toText(description))
}

@PingogramPreviews
@PingogramVariantPreviews
@Composable
private fun FeedbackScreenPreview() {
    PingogramPreview {
        FeedbackScreen(
            state = FeedbackSamples.content(description = "The chat closed when I sent a sticker."),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun FeedbackScreenReportShownPreview() {
    PingogramPreview {
        FeedbackScreen(
            state = FeedbackSamples.content(FeedbackSamples.report.copy(crash = null), showReport = true),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun FeedbackScreenLoadingPreview() {
    PingogramPreview { FeedbackScreen(state = FeedbackUiState.Loading, onEvent = {}, onBack = {}) }
}
