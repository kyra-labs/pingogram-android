package dev.kyralabs.pingogram.feature.auth.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTopBar
import dev.kyralabs.pingogram.feature.auth.ui.components.ConnectionBanner
import dev.kyralabs.pingogram.feature.auth.ui.components.FloodWaitBanner
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

                AuthEffect.Authorized -> {
                    currentOnAuthorized()
                }
            }
        }
    }
    AuthScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier, snackbarHostState = snackbarHostState)
}

/**
 * Every login step in one edge-to-edge [Scaffold]: back navigation and the connection status in the top bar, offline
 * and flood wait banners, snackbars, and the current step, switched with a shared-axis transition.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthScreen(
    state: AuthUiState,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = state.canGoBack) { onEvent(AuthEvent.Back) }
    val motion = MaterialTheme.motionScheme
    val spatial = motion.defaultSpatialSpec<IntOffset>()
    val effects = motion.defaultEffectsSpec<Float>()
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
            FloodWaitBanner(seconds = state.floodWaitSeconds)
            AnimatedContent(
                targetState = state.step,
                transitionSpec = { sharedAxisX(spatial, effects) },
                contentKey = { it::class },
                label = "AuthStep",
            ) { step ->
                AuthStepContent(
                    step = step,
                    canSubmit = state.canSubmit,
                    pendingAction = state.pendingAction,
                    onEvent = onEvent,
                )
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

/** Material shared axis X: forward slides in from the end, back from the start (`docs/DESIGN_SYSTEM.md` §5). */
private fun AnimatedContentTransitionScope<AuthStepUiState>.sharedAxisX(
    spatial: FiniteAnimationSpec<IntOffset>,
    effects: FiniteAnimationSpec<Float>,
): ContentTransform {
    val direction = if (targetState.order >= initialState.order) 1 else -1
    return (slideInHorizontally(spatial) { direction * it / SLIDE_FRACTION } + fadeIn(effects)) togetherWith
        (slideOutHorizontally(spatial) { -direction * it / SLIDE_FRACTION } + fadeOut(effects))
}

/** The position of a step in the flow, for the direction of transitions. */
private val AuthStepUiState.order: Int get() = StepOrder.indexOf(this::class)

private val StepOrder =
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

/** Slide by a tenth of the width, a short travel as in Material's shared axis. */
private const val SLIDE_FRACTION = 10

private fun UriHandler.tryOpen(uri: String) {
    try {
        openUri(uri)
    } catch (_: IllegalArgumentException) {
        // No app can open it (no browser or email app); nothing else to do.
    }
}
