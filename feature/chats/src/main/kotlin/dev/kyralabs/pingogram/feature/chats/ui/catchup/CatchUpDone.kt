package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "All caught up" (the owner's board 32e): the double tick on a `MaterialShapes.Cookie9Sided` badge in `primary`
 * inside a `Cookie12Sided` one in `primaryContainer`, "All caught up" in the wide display style, "12 chats in 4
 * minutes. Nice.", the counts (read, replied, skipped; stacked from 150% font), then "Back to chats" and, with chats
 * skipped, "Review 1 skipped chat", both at Material's medium height. Heard as a status on arrival.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CatchUpDone(
    stage: CatchUpStage.Done,
    onEvent: (CatchUpEvent) -> Unit,
    onBack: () -> Unit,
) {
    val resources = LocalResources.current
    val minutes = resources.getQuantityString(R.plurals.catch_up_done_minutes, stage.minutes, stage.minutes)
    val summary = resources.getQuantityString(R.plurals.catch_up_done_summary, stage.chats, stage.chats, minutes)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium, Alignment.CenterVertically),
    ) {
        DoneIllustration()
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.catch_up_done_title),
                modifier = Modifier.semantics { heading() },
                textAlign = TextAlign.Center,
                style = MaterialTheme.experimentalTypography.displaySmallWide,
            )
            Text(
                text = summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        DoneStats(stage)
        DoneActions(stage.skipped, onEvent, onBack)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DoneIllustration() {
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.avatarProfile)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.headlineBadge)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_done_all),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** The counts as tiles on `surfaceContainerHigh`, side by side, stacked from 150% font. */
@Composable
private fun DoneStats(stage: CatchUpStage.Done) {
    val resources = LocalResources.current
    val stats =
        listOf(
            stage.read to resources.getQuantityString(R.plurals.catch_up_done_read, stage.read),
            stage.replied to resources.getQuantityString(R.plurals.catch_up_done_replied, stage.replied),
            stage.skipped to resources.getQuantityString(R.plurals.catch_up_done_skipped, stage.skipped),
        )
    val tile =
        @Composable { count: Int, label: String, modifier: Modifier ->
            Column(
                modifier =
                    modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                        .padding(MaterialTheme.spacing.medium)
                        .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = count.toString(), style = MaterialTheme.experimentalTypography.displaySmallCondensed)
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) { stats.forEach { (count, label) -> tile(count, label, Modifier.fillMaxWidth()) } }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) { stats.forEach { (count, label) -> tile(count, label, Modifier.weight(1f)) } }
    }
}

@Composable
private fun DoneActions(
    skipped: Int,
    onEvent: (CatchUpEvent) -> Unit,
    onBack: () -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(height),
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
        ) {
            Text(stringResource(R.string.catch_up_back), style = ButtonDefaults.textStyleFor(height))
        }
        if (skipped > 0) {
            val resources = LocalResources.current
            FilledTonalButton(
                onClick = { onEvent(CatchUpEvent.ReviewSkipped) },
                modifier = Modifier.fillMaxWidth().heightIn(height),
                shapes = ButtonDefaults.shapesFor(height),
                contentPadding = ButtonDefaults.contentPaddingFor(height),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next_filled),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                )
                Text(
                    text = resources.getQuantityString(R.plurals.catch_up_review_skipped, skipped, skipped),
                    modifier = Modifier.padding(start = ButtonDefaults.iconSpacingFor(height)),
                    style = ButtonDefaults.textStyleFor(height),
                )
            }
        }
    }
}

/** Nothing to catch up on: the empty state with "Back to chats". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CatchUpEmpty(onBack: () -> Unit) {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_done_all,
        title = stringResource(R.string.catch_up_empty_title),
        body = stringResource(R.string.catch_up_empty_body),
        action = {
            Button(onClick = onBack, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.catch_up_back)) }
        },
    )
}

/**
 * Catch-up's settings: "Catch-up" in `titleLargeEmphasized`, then "Include muted chats and channels" as a switch row
 * (off by default, owner decision 2026-11-15), kept on this device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatchUpSettingsSheet(
    includesMuted: Boolean,
    onIncludesMuted: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    PingogramBottomSheet(onDismissRequest = onDismiss) {
        SettingsSheetContent(includesMuted, onIncludesMuted)
    }
}

@Composable
private fun ColumnScope.SettingsSheetContent(
    includesMuted: Boolean,
    onIncludesMuted: (Boolean) -> Unit,
) {
    Text(
        text = stringResource(R.string.catch_up_settings_title),
        modifier =
            Modifier
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.small)
                .semantics { heading() },
        style = MaterialTheme.typography.titleLargeEmphasized,
    )
    ListItem(
        modifier =
            Modifier
                .padding(bottom = MaterialTheme.spacing.large)
                .toggleable(value = includesMuted, role = Role.Switch, onValueChange = onIncludesMuted),
        supportingContent = { Text(stringResource(R.string.catch_up_include_muted_summary)) },
        trailingContent = { CheckSwitch(checked = includesMuted) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(stringResource(R.string.catch_up_include_muted))
    }
}

private const val LARGE_FONT_SCALE = 1.5f
