package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The phone step's other way in (`docs/DESIGN_SYSTEM.md` §6 "Sign in with a passkey", board 17b): "or" between two
 * lines, then "Sign in with a passkey", a `FilledTonalButton` at Material's medium height across the form, the key on a
 * `Cookie9Sided` badge in `tertiary` (a `LoadingIndicator` in its place while Telegram and the password
 * manager answer), and what it uses below it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PasskeySignInOption(
    enabled: Boolean,
    inProgress: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.auth_passkey_sign_in)
    val height = ButtonDefaults.MediumContainerHeight
    Column(modifier.fillMaxWidth().padding(top = spacing.small)) {
        OrDivider()
        FilledTonalButton(
            onClick = { if (!inProgress) onClick() },
            shapes = ButtonDefaults.shapesFor(height),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = height)
                    .semantics { if (inProgress) contentDescription = label },
            enabled = enabled || inProgress,
            contentPadding = ButtonDefaults.contentPaddingFor(height),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(MaterialTheme.sizes.leadingIconContainer)
                        .background(colors.tertiary, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                if (inProgress) {
                    LoadingIndicator(color = colors.onTertiary)
                } else {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_key),
                        contentDescription = null,
                        tint = colors.onTertiary,
                    )
                }
            }
            Spacer(Modifier.width(spacing.medium))
            Text(text = label, style = ButtonDefaults.textStyleFor(height))
        }
        Text(
            text = stringResource(R.string.auth_passkey_note),
            modifier = Modifier.fillMaxWidth().padding(top = spacing.small, start = spacing.small, end = spacing.small),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "or" between two lines, decorative: screen readers hear the two ways in as they come. */
@Composable
private fun OrDivider() {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.auth_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
