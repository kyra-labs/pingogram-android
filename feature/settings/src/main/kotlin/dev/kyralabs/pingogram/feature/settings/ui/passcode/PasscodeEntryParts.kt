package dev.kyralabs.pingogram.feature.settings.ui.passcode

// The parts of a passcode entry (PasscodeEntry): its badge, its message, the passcode as dots or a password field.

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.window.core.layout.WindowSizeClass
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The decorative badge above the title: the lock on a [MaterialShapes.Gem] in `primaryContainer`, like a login step's
 * badge; while something is wrong ([isError]) it morphs into [MaterialShapes.Cookie4Sided] (the spatial spring) and
 * fades to `errorContainer` with the error icon (the effects spring), as the login steps' badges do.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PasscodeBadge(isError: Boolean) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val shape =
        rememberMorphShape(
            start = MaterialShapes.Gem,
            end = MaterialShapes.Cookie4Sided,
            morphed = isError,
            animationSpec = motion.defaultSpatialSpec(),
        )
    val container by animateColorAsState(
        targetValue = if (isError) colors.errorContainer else colors.primaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "BadgeContainer",
    )
    val content by animateColorAsState(
        targetValue = if (isError) colors.onErrorContainer else colors.onPrimaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "BadgeContent",
    )
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.headlineBadge)
                .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), container) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isError) UiR.drawable.ic_error else UiR.drawable.ic_lock),
            contentDescription = null,
            tint = content,
        )
    }
}

/** [hint], or what went wrong with the last entry in `error`, announced politely to screen readers. */
@Composable
internal fun PasscodeMessageText(
    message: PasscodeMessage?,
    type: PasscodeType,
    hint: String,
) {
    Text(
        text = message?.let { passcodeMessageText(it, type) } ?: hint,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun passcodeMessageText(
    message: PasscodeMessage,
    type: PasscodeType,
): String =
    when (message) {
        PasscodeMessage.Wrong -> {
            stringResource(R.string.settings_passcode_wrong)
        }

        is PasscodeMessage.Wait -> {
            stringResource(R.string.settings_passcode_wait, durationText(message.seconds))
        }

        PasscodeMessage.Invalid -> {
            when (type) {
                PasscodeType.Pin -> stringResource(R.string.settings_passcode_too_short_pin)
                PasscodeType.Password -> stringResource(R.string.settings_passcode_too_short_password)
            }
        }

        PasscodeMessage.Mismatch -> {
            stringResource(R.string.settings_passcode_mismatch)
        }

        is PasscodeMessage.Failed -> {
            errorText(message.error)
        }
    }

/**
 * The digits of a PIN entered so far, one dot each in `onSurface`, in a pill on `surfaceContainerHighest` as tall as a
 * text field; a new dot grows in on the fast spatial spring. Screen readers hear how many digits there are. Shakes
 * for every rejection ([shake]).
 */
@Composable
internal fun PasscodeDots(
    input: PasscodeInput,
    rejections: Int,
) {
    val count = input.state.text.length
    val description =
        if (count == 0) {
            stringResource(R.string.settings_passcode_no_digits)
        } else {
            pluralStringResource(R.plurals.settings_passcode_digits_entered, count, count)
        }
    Row(
        modifier =
            Modifier
                .shake(rejections)
                .widthIn(max = MaterialTheme.sizes.pinPadMaxWidth)
                .fillMaxWidth()
                .heightIn(min = TextFieldDefaults.MinHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                .clearAndSetSemantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { PasscodeDot() }
    }
}

@Composable
private fun PasscodeDot() {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spec) }
    val color = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(MaterialTheme.sizes.passcodeDot)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }.background(color, CircleShape),
    )
}

/**
 * A password: Material's filled [SecureTextField] (`docs/DESIGN_SYSTEM.md` §6, "Text fields": passwords in forms), at
 * most [PasscodeType.Password]'s length, focused at once so the keyboard shows; the keyboard's Done submits it.
 */
@Composable
internal fun PasswordField(
    input: PasscodeInput,
    state: PasscodeEntryUiState,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    SecureTextField(
        state = input.state,
        modifier =
            Modifier
                .shake(state.rejections)
                .fillMaxWidth()
                .focusRequester(focusRequester),
        enabled = state.canSubmit,
        label = { Text(stringResource(R.string.settings_passcode_label)) },
        isError = state.message != null,
        inputTransformation = InputTransformation.maxLength(PasscodeType.Password.maxLength),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        onKeyboardAction = { onSubmit() },
    )
}

/**
 * Shakes sideways each time [rejections] grows: the motion scheme's fast spatial spring, started with a sideways push,
 * swings back and forth around its place a few times as it settles. Read while drawing; with animations off it does
 * not move.
 */
@Composable
private fun Modifier.shake(rejections: Int): Modifier {
    val offset = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val push = with(LocalDensity.current) { MaterialTheme.spacing.extraLarge.toPx() } * SHAKE_PUSH_PER_SECOND
    val composedWith = remember { rejections }
    LaunchedEffect(rejections) {
        if (rejections != composedWith) offset.animateTo(0f, spec, initialVelocity = push)
    }
    return graphicsLayer { translationX = offset.value }
}

/** The shake's push: this many times the extra-large spacing per second. */
private const val SHAKE_PUSH_PER_SECOND = 40f
