package dev.kyralabs.pingogram.feature.auth.ui

import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.SharedAxisDistance
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTopBar
import dev.kyralabs.pingogram.feature.auth.ui.components.ConnectionBanner
import dev.kyralabs.pingogram.feature.auth.ui.components.FloodWaitBanner
import dev.kyralabs.pingogram.feature.auth.ui.components.LocalAuthFloodWait
import kotlinx.coroutines.launch

/**
 * The login flow destination: hoists [AuthViewModel], handles its effects (links, email, snackbars) and calls
 * [onAuthorized] once the account is logged in.
 */
@Composable
fun AuthRoute(
    onAuthorized: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val haptics = LocalHapticFeedback.current
    val currentOnAuthorized by rememberUpdatedState(onAuthorized)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AuthEffect.OpenUrl -> {
                    uriHandler.tryOpen(effect.url)
                }

                is AuthEffect.SendEmail -> {
                    uriHandler.tryOpen("mailto:${effect.address}?subject=${Uri.encode(effect.subject)}")
                }

                // Snackbars queue up; other effects must not wait for them.
                is AuthEffect.ShowError -> {
                    val message = errorMessage(resources, effect.error, showErrorDetails)
                    if (message.isNotEmpty()) launch { snackbarHostState.showSnackbar(message) }
                }

                AuthEffect.InputAccepted -> {
                    haptics.performFeedbackFromApi34(HapticFeedbackType.Confirm)
                }

                AuthEffect.InputRejected -> {
                    haptics.performFeedbackFromApi34(HapticFeedbackType.Reject)
                }

                AuthEffect.Authorized -> {
                    currentOnAuthorized()
                }
            }
        }
    }
    AuthScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier, snackbarHostState = snackbarHostState)
}

/**
 * `Confirm` and `Reject` haptics exist from Android 14 (`docs/DESIGN_SYSTEM.md` §5); earlier versions get none rather
 * than a substitute.
 */
private fun HapticFeedback.performFeedbackFromApi34(type: HapticFeedbackType) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) performHapticFeedback(type)
}

/**
 * Every login step in one edge-to-edge [Scaffold]: back navigation and the connection status in the top bar, offline
 * and flood wait banners (while Telegram asks to wait, every step's badge shows an error too, [LocalAuthFloodWait]),
 * snackbars, and the current step, switched with a Material shared axis Y transition (`docs/DESIGN_SYSTEM.md` §5:
 * vertical flows). System back within the flow follows the predictive back gesture: the transition to the step back
 * leads to ([AuthUiState.backStep]) follows the gesture's progress, and [AuthEvent.Back] goes back once it completes
 * ([rememberAuthStepTransition]).
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val motion = MaterialTheme.motionScheme
    val steps = rememberAuthStepTransition(state = state, onBack = { onEvent(AuthEvent.Back) })
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val enterFade = motion.defaultEffectsSpec<Float>()
    val exitFade = motion.fastEffectsSpec<Float>()
    val distance = with(LocalDensity.current) { SharedAxisDistance.roundToPx() }
    Scaffold(
        modifier = modifier,
        topBar = {
            AuthTopBar(
                connection = state.connection,
                onBack = if (state.canGoBack) ({ onEvent(AuthEvent.Back) }) else null,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
        ) {
            ConnectionBanner(isOffline = state.isOffline)
            FloodWaitBanner(seconds = state.floodWaitSeconds, totalSeconds = state.floodWaitTotalSeconds)
            CompositionLocalProvider(LocalAuthFloodWait provides (state.floodWaitSeconds != null)) {
                steps.transition.AnimatedContent(
                    transitionSpec = { sharedAxisY(spatial, enterFade, exitFade, distance) },
                ) { key ->
                    AuthStepContent(
                        step = steps.stepOf(key, state.step),
                        canSubmit = state.canSubmit,
                        pendingAction = state.pendingAction,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthStepContent(
    step: AuthStepUiState,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
) {
    when (step) {
        AuthStepUiState.Loading, AuthStepUiState.Done -> AuthProgressScreen(isLoggingOut = false)
        AuthStepUiState.LoggingOut -> AuthProgressScreen(isLoggingOut = true)
        AuthStepUiState.Welcome -> WelcomeScreen(onEvent = onEvent)
        is AuthStepUiState.PhoneNumber -> PhoneNumberScreen(step, canSubmit, pendingAction, onEvent)
        is AuthStepUiState.QrCode -> QrCodeScreen(step, pendingAction, onEvent)
        is AuthStepUiState.Code -> CodeScreen(step, canSubmit, pendingAction, onEvent)
        is AuthStepUiState.Password -> PasswordScreen(step, canSubmit, pendingAction, onEvent)
        is AuthStepUiState.PasswordRecoveryCode -> PasswordRecoveryCodeScreen(step, canSubmit, pendingAction, onEvent)
        is AuthStepUiState.NewPassword -> NewPasswordScreen(step, canSubmit, pendingAction, onEvent)
        is AuthStepUiState.Registration -> RegistrationScreen(step, canSubmit, pendingAction, onEvent)
        is AuthStepUiState.EmailAddress -> EmailAddressScreen(step, canSubmit, pendingAction, onEvent)
        is AuthStepUiState.EmailCode -> EmailCodeScreen(step, canSubmit, pendingAction, onEvent)
        is AuthStepUiState.PremiumRequired -> PremiumRequiredScreen(step, onEvent)
    }
}

/**
 * Material shared axis Y (`docs/DESIGN_SYSTEM.md` §5): going forward, the next step rises in from below while the
 * current one rises out, both by [distance] px and fading; going back, the reverse. Spatial and effects springs from
 * `MaterialTheme.motionScheme`.
 */
private fun AnimatedContentTransitionScope<StepKey>.sharedAxisY(
    spatial: FiniteAnimationSpec<IntOffset>,
    enterFade: FiniteAnimationSpec<Float>,
    exitFade: FiniteAnimationSpec<Float>,
    distance: Int,
): ContentTransform {
    val direction = if (targetState.order >= initialState.order) 1 else -1
    return (slideInVertically(spatial) { direction * distance } + fadeIn(enterFade)) togetherWith
        (slideOutVertically(spatial) { -direction * distance } + fadeOut(exitFade))
}

/** The position of a step in the flow, for the direction of transitions. */
private val StepKey.order: Int get() = StepOrder.indexOf(this)

private val StepOrder: List<StepKey> =
    listOf(
        AuthStepUiState.Loading::class,
        AuthStepUiState.Welcome::class,
        AuthStepUiState.PhoneNumber::class,
        AuthStepUiState.QrCode::class,
        AuthStepUiState.Code::class,
        AuthStepUiState.EmailAddress::class,
        AuthStepUiState.EmailCode::class,
        AuthStepUiState.PremiumRequired::class,
        AuthStepUiState.Password::class,
        AuthStepUiState.PasswordRecoveryCode::class,
        AuthStepUiState.NewPassword::class,
        AuthStepUiState.Registration::class,
        AuthStepUiState.LoggingOut::class,
        AuthStepUiState.Done::class,
    )

private fun UriHandler.tryOpen(uri: String) {
    try {
        openUri(uri)
    } catch (_: IllegalArgumentException) {
        // No app can open it (no browser or email app); nothing else to do.
    }
}
