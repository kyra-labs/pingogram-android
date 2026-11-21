package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Apply on a chat's wallpaper page (ADR 0041): a small sheet with "Apply for me" and "Apply for me and <name>"
 * ([chatName]), which sets it for the other person too. Without Telegram Premium ([canSetForBoth] false) the second
 * shows a lock and "Requires Telegram Premium" and is disabled: information only, no purchase flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WallpaperApplySheet(
    chatName: String?,
    canSetForBoth: Boolean,
    onApply: (forBoth: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    PingogramBottomSheet(onDismissRequest = onDismiss) {
        WallpaperApplyChoices(chatName, canSetForBoth, onApply)
    }
}

/** The sheet's heading and its two choices, as a segmented group. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WallpaperApplyChoices(
    chatName: String?,
    canSetForBoth: Boolean,
    onApply: (forBoth: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val forBoth =
        chatName?.let { stringResource(R.string.settings_wallpaper_apply_for_both, it) }
            ?: stringResource(R.string.settings_wallpaper_apply_for_both_unnamed)
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        Text(
            text = stringResource(R.string.settings_wallpaper_apply_title),
            modifier = Modifier.padding(bottom = spacing.medium).semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        SettingsRow(
            title = stringResource(R.string.settings_wallpaper_apply_for_me),
            shapes = ListItemDefaults.segmentedShapes(0, 2),
            icon = UiR.drawable.ic_person_filled,
            onClick = { onApply(false) },
        )
        SettingsRow(
            title = forBoth,
            shapes = ListItemDefaults.segmentedShapes(1, 2),
            icon = if (canSetForBoth) UiR.drawable.ic_group else UiR.drawable.ic_lock,
            supportingContent =
                if (canSetForBoth) {
                    null
                } else {
                    { Text(stringResource(R.string.settings_wallpaper_requires_premium)) }
                },
            onClick = { onApply(true) },
            enabled = canSetForBoth,
        )
    }
}

@PingogramPreviews
@Composable
private fun WallpaperApplyChoicesPreview() {
    PingogramPreview {
        WallpaperApplyChoices(chatName = "Meera", canSetForBoth = false, onApply = {})
    }
}
