package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * A status of the account: a full pill with `labelMediumEmphasized` text, "Premium" in `tertiaryContainer`. Not a
 * chip: it does nothing when tapped, and Pingogram sells nothing (`REQUIREMENTS.md` §1.2).
 */
@Composable
internal fun AccountLabel(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Text(
        text = text,
        modifier =
            modifier
                .background(container, CircleShape)
                .padding(horizontal = spacing.small, vertical = spacing.extraSmall / 2),
        color = content,
        style = MaterialTheme.typography.labelMediumEmphasized,
    )
}
