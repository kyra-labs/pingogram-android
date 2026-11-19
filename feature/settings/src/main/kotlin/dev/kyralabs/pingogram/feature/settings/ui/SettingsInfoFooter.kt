package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The explanation at the bottom of a page with a master switch (`docs/DESIGN_SYSTEM.md` §13.10 "Pages with a master
 * switch", owner decision 2026-11-19): Material Symbols' `info` in `onSurfaceVariant`, decorative, above [text] in
 * `bodyMedium` `onSurfaceVariant`, inset as the groups' footers are.
 */
@Composable
internal fun SettingsInfoFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        Icon(painterResource(UiR.drawable.ic_info), contentDescription = null, tint = colors.onSurfaceVariant)
        Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@PingogramPreviews
@Composable
private fun SettingsInfoFooterPreview() {
    PingogramPreview {
        SettingsInfoFooter(
            "Raise the phone to your ear while a voice message plays to hear it through the earpiece.",
            Modifier.padding(MaterialTheme.spacing.large),
        )
    }
}
