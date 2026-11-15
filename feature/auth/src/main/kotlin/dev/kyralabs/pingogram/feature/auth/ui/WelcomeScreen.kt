package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.AvatarColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPaneLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.isCompactWidthWindow
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * FR-A1 (`docs/DESIGN_SYSTEM.md` §6 "Welcome", board 23): Pingogram's logo among chat avatars ([WelcomeIllustration]),
 * the wordmark as the heading, what Pingogram is ("Your Telegram account, in Material 3 Expressive.") and an honest
 * note that it is not Telegram's own app (CLAUDE.md rule 3), "Start messaging", Telegram's terms of service and
 * privacy policy, and Pingogram's own privacy policy. The logo morphs while "Start messaging" is pressed. On wide
 * windows and in landscape the illustration and the wordmark are the start pane, the rest the end one.
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
            WelcomeIllustration(isMorphed = isStartPressed, onHold = { onEvent(AuthEvent.ShowDemoDialog) })
            Wordmark()
        },
        actions = { WelcomeActions(startInteractions = startInteractions, onEvent = onEvent) },
    ) {
        Text(
            text = stringResource(R.string.auth_welcome_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.auth_welcome_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Pingogram's wordmark (`art/logo_full_mono.svg`) as the screen's heading, "Pingogram" for screen readers, in
 * `onSurface`: one color from the theme, like the mark (`docs/DESIGN_SYSTEM.md` §8). Three quarters of the width, at
 * most as wide as the logo's illustration lets it.
 */
@Composable
private fun Wordmark() {
    val title = stringResource(R.string.auth_welcome_title)
    Icon(
        painter = painterResource(R.drawable.pingogram_wordmark),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .widthIn(max = MaterialTheme.sizes.welcomeIllustrationMaxSize * WORDMARK_WIDTH_FRACTION)
                .fillMaxWidth(WORDMARK_WIDTH_FRACTION)
                .aspectRatio(WORDMARK_ASPECT_RATIO)
                .semantics {
                    heading()
                    contentDescription = title
                },
    )
}

/**
 * "Start messaging", the hero moment's medium button (`docs/DESIGN_SYSTEM.md` §6): as wide as the column on compact
 * windows, as wide as its label on wider ones. Then Telegram's terms of service and privacy policy, which continuing
 * accepts, and Pingogram's own privacy policy, each a button of its own.
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
        trailingIcon = R.drawable.ic_arrow_forward,
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
            onClick = { onEvent(AuthEvent.OpenTelegramPrivacyPolicy) },
        )
        AuthTextButton(
            text = stringResource(R.string.auth_pingogram_privacy_policy),
            onClick = { onEvent(AuthEvent.OpenPingogramPrivacyPolicy) },
        )
    }
}

private const val WORDMARK_WIDTH_FRACTION = 0.75f

/** The wordmark's width over its height (`art/logo_full_mono.svg`, without the mark: 456 by 77). */
private const val WORDMARK_ASPECT_RATIO = 456f / 77f

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
