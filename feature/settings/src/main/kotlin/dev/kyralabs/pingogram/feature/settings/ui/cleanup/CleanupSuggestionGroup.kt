package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListItem

/**
 * Below Cleanup suggestions' settings (`REQUIREMENTS.md` FR-X24), while they are on and no chat is suggested: a loading
 * indicator while the main list loads, then a line saying there is nothing to clean up.
 */
@Composable
internal fun CleanupSuggestionGroup(state: CleanupUiState) {
    val settings = state.settings ?: return
    if (!settings.enabled || state.suggestion != null) return
    if (state.loading) {
        Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large), contentAlignment = Alignment.Center) {
            DelayedLoadingIndicator()
        }
    } else {
        SettingsGroup {
            item { shapes ->
                SettingsListItem(
                    shapes = shapes,
                    supportingContent = { Text(stringResource(R.string.settings_cleanup_none_body)) },
                ) {
                    Text(stringResource(R.string.settings_cleanup_none))
                }
            }
        }
    }
}
