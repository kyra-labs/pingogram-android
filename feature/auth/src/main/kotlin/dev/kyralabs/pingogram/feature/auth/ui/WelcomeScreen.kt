package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPaneLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.isCompactHeightWindow
import dev.kyralabs.pingogram.feature.auth.ui.components.isCompactWidthWindow
import dev.kyralabs.pingogram.feature.auth.ui.components.squareOfWidth
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * FR-A1: the brand (`displayMediumEmphasized`), an honest note that Pingogram is not Telegram's own app (CLAUDE.md
 * rule 3), "Start messaging", and Telegram's terms of service and privacy policy. The hero, Pingogram's mark on an
 * Expressive shape, morphs while "Start messaging" is pressed. On wide windows and in landscape the hero and the brand
 * are the start pane, the rest the end one.
 */
@Composable
fun WelcomeScreen(
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val startInteractions = remember { MutableInteractionSource() }
    val isStartPressed by startInteractions.collectIsPressedAsState()
    AuthPaneLayout(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        centerVertically = true,
        header = {
            WelcomeHero(isMorphed = isStartPressed)
            val brandStyle = MaterialTheme.typography.displayMediumEmphasized
            Text(
                text = stringResource(R.string.auth_welcome_title),
                style = brandStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                // One word that must not break: it shrinks instead when it does not fit (narrow window, 200% font).
                autoSize = TextAutoSize.StepBased(maxFontSize = brandStyle.fontSize),
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
        },
        actions = { WelcomeActions(startInteractions = startInteractions, onEvent = onEvent) },
    ) {
        Text(
            text = stringResource(R.string.auth_welcome_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.auth_welcome_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * "Start messaging", the hero moment's medium button (`docs/DESIGN_SYSTEM.md` §6): as wide as the column on compact
 * windows, as wide as its label on wider ones. Then the terms of service and privacy policy that continuing accepts.
 */
@Composable
private fun ColumnScope.WelcomeActions(
    startInteractions: MutableInteractionSource,
    onEvent: (AuthEvent) -> Unit,
) {
    AuthPrimaryButton(
        text = stringResource(R.string.auth_start_messaging),
        onClick = { onEvent(AuthEvent.StartMessaging) },
        enabled = true,
        inProgress = false,
        modifier = if (isCompactWidthWindow()) Modifier.fillMaxWidth() else Modifier,
        hero = true,
        interactionSource = startInteractions,
    )
    Text(
        text = stringResource(R.string.auth_welcome_agreement),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.CenterHorizontally),
    ) {
        AuthTextButton(
            text = stringResource(R.string.auth_terms_of_service),
            onClick = { onEvent(AuthEvent.OpenTermsOfService) },
        )
        AuthTextButton(
            text = stringResource(R.string.auth_privacy_policy),
            onClick = { onEvent(AuthEvent.OpenPrivacyPolicy) },
        )
    }
}

/**
 * Decorative brand artwork: Pingogram's mark (`art/`) on a [MaterialShapes] cookie that morphs into a soft burst while
 * [isMorphed] (a fast spatial spring, `docs/DESIGN_SYSTEM.md` §5: press feedback).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeHero(isMorphed: Boolean) {
    val shape =
        rememberMorphShape(
            start = MaterialShapes.Cookie9Sided,
            end = MaterialShapes.SoftBurst,
            morphed = isMorphed,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        )
    // In landscape on a phone the artwork would fill the height; it shrinks so the brand stays in view.
    val maxSize = MaterialTheme.sizes.illustrationMaxSize * if (isCompactHeightWindow()) COMPACT_HEIGHT_SCALE else 1f
    Box(
        modifier =
            Modifier
                .squareOfWidth(HERO_WIDTH_FRACTION, maxSize)
                .background(MaterialTheme.colorScheme.primaryContainer, shape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_pingogram),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxSize(HERO_ICON_FRACTION),
        )
    }
}

private const val HERO_WIDTH_FRACTION = 0.5f
private const val HERO_ICON_FRACTION = 0.4f
private const val COMPACT_HEIGHT_SCALE = 0.5f

@PingogramPreviews
@Composable
private fun WelcomeScreenPreview() {
    AuthScreenPreview(AuthPreviewData.welcome)
}

@PingogramPreviews
@Composable
private fun LoadingScreenPreview() {
    AuthScreenPreview(AuthPreviewData.loading)
}

@PingogramPreviews
@Composable
private fun LoggingOutScreenPreview() {
    AuthScreenPreview(AuthPreviewData.loggingOut)
}
