package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Saved Messages' tags as a row of `FilterChip`s (`FilterChipDefaults.shapes()`, one radio group for screen readers):
 * "All" first, then each tag's emoji, label and count (`labelMedium`, tabular figures), in a horizontal scroll inside
 * the window margin; a tap filters by the tag, another tap or "All" shows everything again. Each tag's chip offers
 * "Rename" as a custom action. Nothing while there are no tags.
 */
@Composable
internal fun SavedTagChips(
    state: SavedTagsUiState,
    onSelect: (SavedTagChip?) -> Unit,
    onRename: (SavedTagChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.tags.isEmpty()) return
    val margin = windowMargin()
    val renameLabel = stringResource(R.string.saved_tags_rename)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = margin, vertical = MaterialTheme.spacing.extraSmall)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        FilterChip(
            selected = state.selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.saved_tab_all)) },
            modifier = Modifier.semantics { role = Role.RadioButton },
            shapes = FilterChipDefaults.shapes(),
        )
        state.tags.forEach { tag ->
            val selected = tag == state.selected
            FilterChip(
                selected = selected,
                onClick = { onSelect(if (selected) null else tag) },
                label = { TagLabel(tag) },
                modifier =
                    Modifier.semantics {
                        role = Role.RadioButton
                        customActions = listOf(CustomAccessibilityAction(renameLabel) { onRename(tag).let { true } })
                    },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}

/** A tag's emoji (or the custom emoji's symbol), its label as user content, and its count. */
@Composable
private fun TagLabel(tag: SavedTagChip) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (val reaction = tag.reaction) {
            is ReactionType.Emoji -> Text(reaction.emoji)
            else -> Icon(painterResource(UiR.drawable.ic_mood), contentDescription = null)
        }
        if (tag.label.isNotEmpty()) Text(tag.label, style = MaterialTheme.typography.labelLarge.userContent)
        Text(tag.count.toString(), style = MaterialTheme.typography.labelMedium)
    }
}

/** "❤ Ideas", "❤": a tag as its emoji and label, for titles and screen readers. */
@Composable
internal fun SavedTagChip.name(): String {
    val emoji = (reaction as? ReactionType.Emoji)?.emoji ?: stringResource(R.string.saved_tags_custom_emoji)
    return if (label.isEmpty()) emoji else "$emoji $label"
}
