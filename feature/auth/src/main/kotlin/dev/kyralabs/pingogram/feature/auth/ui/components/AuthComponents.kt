package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The layout of a login step: its title (`titleLargeEmphasized`, `docs/DESIGN_SYSTEM.md` §3.3) as a heading, an
 * optional description and [content], with [actions] below, in the adaptive [AuthPaneLayout].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AuthStepLayout(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    AuthPaneLayout(modifier = modifier, actions = actions) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

/**
 * The step's main action: a full-width medium Expressive button whose shape morphs when pressed
 * (`ButtonDefaults.shapesFor`, a `motionScheme` spring). While its request runs ([inProgress]) it shows a
 * [LoadingIndicator] and keeps [text] for screen readers. [interactionSource] lets the screen react to presses too.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = { if (!inProgress) onClick() },
        shapes = ButtonDefaults.shapesFor(height),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .semantics { if (inProgress) contentDescription = text },
        enabled = enabled || inProgress,
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        interactionSource = interactionSource,
    ) {
        if (inProgress) {
            LoadingIndicator(
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                color = LocalContentColor.current,
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** A secondary action under the primary button, e.g. "Forgot password?"; shows progress like [AuthPrimaryButton]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AuthTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inProgress: Boolean = false,
) {
    val height = ButtonDefaults.MinHeight
    TextButton(
        onClick = { if (!inProgress) onClick() },
        shapes = ButtonDefaults.shapesFor(height),
        modifier = modifier.semantics { if (inProgress) contentDescription = text },
        enabled = enabled || inProgress,
    ) {
        if (inProgress) {
            LoadingIndicator(
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                color = LocalContentColor.current,
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** A notice inside a step, on a tonal surface: e.g. why a password cannot be recovered. */
@Composable
internal fun AuthNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(MaterialTheme.spacing.large),
        )
    }
}

/** A square whose side is [fraction] of the available width, at most [maxSize]: hero artwork, the QR code frame. */
internal fun Modifier.squareOfWidth(
    fraction: Float,
    maxSize: Dp,
): Modifier =
    layout { measurable, constraints ->
        val side = min((constraints.maxWidth * fraction).roundToInt(), maxSize.roundToPx())
        val placeable = measurable.measure(Constraints.fixed(side, side))
        layout(side, side) { placeable.place(0, 0) }
    }

/** As wide as tall, the larger of the content's width and height, with the content centered: a badge. */
internal fun Modifier.squareAroundContent(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val side = max(placeable.width, placeable.height)
        layout(side, side) { placeable.place((side - placeable.width) / 2, (side - placeable.height) / 2) }
    }
