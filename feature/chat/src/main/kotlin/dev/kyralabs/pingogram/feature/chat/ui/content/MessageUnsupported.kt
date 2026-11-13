package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R

/**
 * A message of a kind this version of Pingogram cannot show (`docs/DESIGN_SYSTEM.md` §6 "Unsupported messages"),
 * padded in its bubble: that it cannot be shown yet, and, where [showsUpdateLink], "Update Pingogram", which opens the
 * latest release's page ([onUpdate]); builds an app store installed leave the link out, as the store updates them
 * (`AppInfo.isFromAppStore`, as About does).
 */
@Composable
internal fun MessageUnsupported(
    showsUpdateLink: Boolean,
    colors: BubbleColors,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        Text(
            text = stringResource(R.string.content_unsupported_body),
            color = colors.secondaryContent,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (showsUpdateLink) {
            TextButton(
                onClick = onUpdate,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
            ) {
                Text(stringResource(R.string.content_update_app))
            }
        }
    }
}

@Preview
@Composable
private fun MessageUnsupportedPreview() {
    PingogramTheme(dynamicColor = false) {
        MessageUnsupported(showsUpdateLink = true, colors = MaterialTheme.pingogramColors.incomingBubble, onUpdate = {})
    }
}
