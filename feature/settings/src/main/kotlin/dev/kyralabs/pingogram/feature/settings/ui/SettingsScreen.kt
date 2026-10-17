package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The Settings tab until the settings screens land (`docs/ROADMAP.md` M3): an empty state. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier) { innerPadding ->
        EmptyState(
            illustration = MaterialShapes.Sunny,
            icon = UiR.drawable.ic_settings_filled,
            title = stringResource(R.string.settings_placeholder_title),
            body = stringResource(R.string.settings_placeholder_body),
            modifier = Modifier.padding(innerPadding),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@PingogramPreviews
@Composable
private fun SettingsScreenPreview() {
    PingogramPreview { SettingsScreen() }
}
