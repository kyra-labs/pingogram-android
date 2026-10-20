package dev.kyralabs.pingogram.feature.settings.ui.passcode

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
import androidx.compose.runtime.Immutable
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
 * A passcode entry, the same on the lock screen, before the passcode settings and when setting a passcode: a
 * [PasscodeBadge], the title (`headlineMediumEmphasized`, as a login step's), the hint or what went wrong
 * ([PasscodeEntryUiState.message], in `error`), and the passcode: dots and the [PinPad] for a PIN, a
 * [SecureTextField] on the keyboard for a password. A rejected entry shakes the field on a spatial spring and buzzes
 * ([HapticFeedbackType.Reject]).
 *
 * In one column on phones in portrait; on windows of compact height (phones in landscape) the header, the dots and the
 * actions sit at the start and the keypad alone at the end, so it never needs scrolling (a password's field and actions
 * take the end). Both scroll when they do not fit
 * (large font scales). [secondaryAction] (e.g. "Forgot passcode?") sits at the bottom start, [extraAction] (e.g. the
 * fingerprint button) at the bottom end; for a password the submit button follows it.
 */
@Composable
internal fun PasscodeEntry(
    texts: PasscodeEntryTexts,
    state: PasscodeEntryUiState,
    input: PasscodeInput,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryAction: (@Composable () -> Unit)? = null,
    extraAction: (@Composable () -> Unit)? = null,
) {
    RejectionHaptics(state.rejections)
    val header: @Composable ColumnScope.() -> Unit = {
        PasscodeBadge(isError = state.message != null)
        Text(
            text = texts.title,
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMediumEmphasized,
        )
        PasscodeMessageText(state.message, state.type, texts.hint)
    }
    val pin = state.type == PasscodeType.Pin
    val field: @Composable ColumnScope.() -> Unit = {
        if (pin) PasscodeDots(input, state.rejections) else PasswordField(input, state, onSubmit)
    }
    val pad: @Composable ColumnScope.() -> Unit = {
        if (pin) {
            PinPad(
                input = input,
                onSubmit = onSubmit,
                submitLabel = texts.submit,
                enabled = state.canSubmit,
                isChecking = state.isChecking,
            )
        }
    }
    val actions: @Composable ColumnScope.() -> Unit = {
        EntryActions(
            secondary = secondaryAction,
            extra = extraAction,
            submit =
                if (pin) {
                    null
                } else {
                    { SubmitButton(texts.submit, onSubmit, enabled = state.canSubmit, isChecking = state.isChecking) }
                },
        )
    }
    PasscodeEntryLayout(pin, header, field, pad, actions, modifier)
}

/**
 * Lays out an entry's [header], [field], [pad] and [actions]: one column, or on windows of compact height two, the
 * keypad of a PIN ([pin]) alone at the end, where it fits a phone's height in landscape; a password's field and
 * actions at the end.
 */
@Composable
private fun PasscodeEntryLayout(
    pin: Boolean,
    header: @Composable ColumnScope.() -> Unit,
    field: @Composable ColumnScope.() -> Unit,
    pad: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactHeight =
        !currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(
            WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
        )
    if (compactHeight) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
        ) {
            EntryPane(Modifier.weight(1f).fillMaxHeight()) {
                header()
                if (pin) {
                    field()
                    actions()
                }
            }
            EntryPane(Modifier.weight(1f).fillMaxHeight()) {
                if (pin) {
                    pad()
                } else {
                    field()
                    actions()
                }
            }
        }
    } else {
        EntryPane(modifier.fillMaxSize()) {
            header()
            field()
            pad()
            actions()
        }
    }
}

/** A column of the entry, centered and scrolling when it does not fit, at most as wide as a column of content. */
@Composable
private fun EntryPane(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.windowMarginCompact),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large, Alignment.CenterVertically),
    ) {
        Spacer(Modifier.weight(1f, fill = false))
        Column(
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            content = content,
        )
        Spacer(Modifier.weight(1f, fill = false))
    }
}

/** Buzzes once for every rejected entry after the first composition ([HapticFeedbackType.Reject], §5). */
@Composable
private fun RejectionHaptics(rejections: Int) {
    val haptics = LocalHapticFeedback.current
    val composedWith = remember { rejections }
    LaunchedEffect(rejections) {
        if (rejections != composedWith) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }
}

/** The entry's actions below it: [secondary] at the start; [extra] and [submit] at the end. */
@Composable
private fun EntryActions(
    secondary: (@Composable () -> Unit)?,
    extra: (@Composable () -> Unit)?,
    submit: (@Composable () -> Unit)?,
) {
    if (secondary == null && extra == null && submit == null) return
    Row(
        modifier = Modifier.widthIn(max = MaterialTheme.sizes.pinPadMaxWidth).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        secondary?.invoke()
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            extra?.invoke()
            submit?.invoke()
        }
    }
}
