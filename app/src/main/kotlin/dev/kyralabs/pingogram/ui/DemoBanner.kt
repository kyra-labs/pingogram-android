package dev.kyralabs.pingogram.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The demo's banner (`docs/adr/0034-demo-mode-for-store-review.md`, `docs/DESIGN_SYSTEM.md` §6 "Demo"): while the demo
 * shows, "Demo · Sample chats on this device. Not connected to Telegram." on `tertiaryContainer` under the status bar,
 * above every screen of the app, which lays out below it ([content] gets the status bar's insets as consumed). Without
 * [isDemo], [content] alone.
 */
@Composable
internal fun DemoFrame(
    isDemo: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!isDemo) {
        Column(Modifier.fillMaxSize(), content = content)
        return
    }
    Column(Modifier.fillMaxSize()) {
        DemoBanner()
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars),
            content = content,
        )
    }
}

/** The banner itself: "Demo" in the emphasized label style, then what the demo is, in at most two lines. */
@Composable
internal fun DemoBanner(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.demo_banner_label)
    val explanation = stringResource(R.string.demo_banner_text)
    val emphasis = MaterialTheme.typography.labelLargeEmphasized.toSpanStyle()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = emphasis.fontWeight, fontFamily = emphasis.fontFamily)) {
                        append(label)
                    }
                    append(stringResource(R.string.demo_banner_separator))
                    append(explanation)
                },
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            modifier =
                Modifier
                    .windowInsetsPadding(
                        WindowInsets.statusBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ).padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
                    // Screen readers meet it first on every screen, as a heading they can jump past.
                    .semantics { heading() },
        )
    }
}

@PingogramPreviews
@Composable
private fun DemoBannerPreview() {
    PingogramPreview { DemoBanner() }
}
