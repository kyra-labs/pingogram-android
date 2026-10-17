package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The layout of every login step: a title (`titleLargeEmphasized`, `docs/DESIGN_SYSTEM.md` §3.3), an optional
 * description and the step's [content], which scroll when they do not fit (font scale 200%, landscape), and
 * [actions], kept at the bottom above the keyboard.
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
    val spacing = MaterialTheme.spacing
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.windowMarginCompact, vertical = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
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
        Column(
            modifier = Modifier.padding(horizontal = spacing.windowMarginCompact, vertical = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            content = actions,
        )
    }
}

/**
 * The step's main action: a full-width medium Expressive button whose shape morphs when pressed. While its request
 * runs ([inProgress]) it shows a [LoadingIndicator] and keeps [text] for screen readers.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
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
