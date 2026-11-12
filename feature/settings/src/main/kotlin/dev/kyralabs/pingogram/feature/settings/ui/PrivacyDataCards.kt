package dev.kyralabs.pingogram.feature.settings.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
import dev.kyralabs.pingogram.core.ui.component.GrowingVisibility
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R

// The cards at the top of Privacy and security and Data and storage
// (docs/DESIGN_SYSTEM.md §13.10).

/**
 * "Privacy checkup": how many privacy settings let anyone on Telegram see the user's things, the settings' names as
 * tags, "Review", which opens the first of them, and "Not now", which hides the card until the page opens again. None
 * while no setting is open to everybody. On `primaryContainer` with `extraLarge` corners.
 */
@Composable
internal fun PrivacyCheckupCard(
    state: PrivacySecurityUiState.Content,
    onOpen: (PrivacySecurityPage) -> Unit,
) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val open = PrivacySettings.filter { state.rules[it]?.choice == PrivacyChoice.Everybody }
    if (dismissed || open.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val resources = LocalResources.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Column(Modifier.padding(spacing.large), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
            val description = resources.getQuantityString(R.plurals.settings_privacy_checkup_what, open.size)
            Column(
                Modifier.semantics(mergeDescendants = true) { heading() },
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) {
                Text(
                    stringResource(R.string.settings_privacy_checkup),
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(open.size.toString(), style = MaterialTheme.experimentalTypography.displaySmallCondensed)
                    Text(description, style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
            CheckupTags(open)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                Button(
                    onClick = { onOpen(PrivacySecurityPage.Setting(open.first())) },
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colors.onPrimaryContainer,
                            contentColor = colors.primaryContainer,
                        ),
                ) { Text(stringResource(R.string.settings_privacy_checkup_review)) }
                TextButton(
                    onClick = { dismissed = true },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer),
                ) { Text(stringResource(R.string.settings_privacy_checkup_not_now)) }
            }
        }
    }
}

/**
 * The storage Pingogram takes on the device: the total in a big condensed figure over "used on this device", a bar
 * of its parts (the downloaded files in `primary`, the databases in `tertiary`), their sizes beside their colors, and
 * "Clear cache" ([onClearCache], which asks first). On the rows' container with `extraLarge` corners; the bar is one
 * item for screen readers, saying the parts' sizes.
 */
@Composable
internal fun StorageCard(
    usage: StorageUsage,
    onClearCache: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val files = Formatter.formatShortFileSize(context, usage.filesSize)
    val database = Formatter.formatShortFileSize(context, usage.databaseSize)
    val parts =
        listOf(
            StoragePart(stringResource(R.string.settings_storage_files), files, usage.filesSize, colors.primary),
            StoragePart(
                stringResource(R.string.settings_storage_database),
                database,
                usage.databaseSize,
                colors.tertiary,
            ),
        )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = settingsRowContainerColor(),
    ) {
        Column(Modifier.padding(spacing.large), verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    Formatter.formatShortFileSize(context, usage.filesSize + usage.databaseSize),
                    style = MaterialTheme.experimentalTypography.displaySmallCondensed.numericContent,
                )
                Text(
                    stringResource(R.string.settings_storage_used_on_device),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StorageParts(parts)
            Button(onClick = onClearCache, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.settings_clear_cache))
            }
        }
    }
}

/** The settings open to everybody, as tags on the checkup card. */
@Composable
private fun CheckupTags(open: List<PrivacySetting>) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        open.forEach { setting ->
            Text(
                text = stringResource(setting.titleRes()),
                modifier =
                    Modifier
                        .background(colors.onPrimaryContainer.copy(alpha = TAG_ALPHA), CircleShape)
                        .padding(horizontal = spacing.medium, vertical = spacing.extraSmall),
                style = MaterialTheme.typography.labelMediumEmphasized,
            )
        }
    }
}

/**
 * The parts of the storage: a bar of their shares (each at least a fiftieth, so a small one stays visible), one item
 * for screen readers that says their sizes, then each part's color, name and size.
 */
@Composable
private fun StorageParts(parts: List<StoragePart>) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val total = parts.sumOf { it.bytes }.coerceAtLeast(1L)
    val barDescription = parts.joinToString { "${it.label} ${it.size}" }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(spacing.large)
                .semantics { contentDescription = barDescription },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.sizes.mediaAlbumGap),
    ) {
        parts.forEach { part ->
            Box(
                Modifier
                    .weight(part.bytes.coerceAtLeast(total / MIN_PART_SHARE).toFloat())
                    .height(spacing.large)
                    .background(part.color, MaterialTheme.shapes.small),
            )
        }
    }
    parts.forEach { part ->
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(spacing.medium).background(part.color, MaterialTheme.shapes.extraSmall))
            Text(part.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMediumEmphasized)
            Text(part.size, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.numericContent)
        }
    }
}

private class StoragePart(
    val label: String,
    val size: String,
    val bytes: Long,
    val color: Color,
)

/** How much of a tag's text color fills its pill. */
private const val TAG_ALPHA = 0.12f

/** The least a part of the bar shows, as a share of the total: one fiftieth, so a small part stays visible. */
private const val MIN_PART_SHARE = 50L

/**
 * The storage card while the statistics have answered ([usage] is content): it grows in and shrinks away on the motion
 * scheme's springs, showing the last usage it had on its way out, with the page's gap below it if [gapBelow].
 */
@Composable
internal fun StorageCardSlot(
    usage: StorageUsageState,
    gapBelow: Boolean,
    onClearCache: () -> Unit,
) {
    val last = remember { LastUsage() }
    if (usage is StorageUsageState.Content) last.value = usage.usage
    GrowingVisibility(visible = usage is StorageUsageState.Content) {
        last.value?.let { shown ->
            Box(if (gapBelow) Modifier.padding(bottom = settingsGroupSpacing()) else Modifier) {
                StorageCard(shown, onClearCache)
            }
        }
    }
}

/** The usage the card showed last, kept for its way out; not state, as only the leaving card reads it. */
private class LastUsage {
    var value: StorageUsage? = null
}
