package dev.kyralabs.pingogram.feature.chat.ui.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatSearchEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatSearchPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The middle of the search's bar: in groups "Search by sender" (the members' sheet) and the counter, centred in what
 * is left ([SearchCounter]); or the picked sender's chip ([SenderChip]), taking what the counter leaves, with the
 * counter after it, beside the arrows.
 */
@Composable
internal fun RowScope.SenderAndCounter(
    state: ChatSearchUiState,
    onEvent: (ChatEvent) -> Unit,
) {
    val sender = state.sender
    val results = state.results
    val onRetry = { onEvent(ChatSearchEvent.Retry) }
    if (sender != null) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SenderChip(sender, onRemove = { onEvent(ChatSearchEvent.ClearSender) }, Modifier.weight(1f, fill = false))
            SearchCounter(results, onRetry, Modifier.padding(horizontal = MaterialTheme.spacing.small))
        }
    } else {
        if (state.canPickSender) {
            BarButton(R.drawable.ic_person_search, stringResource(R.string.search_by_sender)) {
                onEvent(ChatSearchEvent.OpenSenders)
            }
        }
        SearchCounter(results, onRetry, Modifier.weight(1f))
    }
}

/**
 * "3 of 17" in `labelLarge` with tabular figures, "Searching…", "No results", or after a failure "Try again"
 * ([onRetry]); nothing before anything is searched. A polite live region, so screen readers hear it change.
 */
@Composable
private fun SearchCounter(
    results: SearchResults,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results is SearchResults.Failed) {
        val failed = stringResource(R.string.search_failed)
        Box(modifier, contentAlignment = Alignment.Center) {
            TextButton(
                onClick = onRetry,
                shapes = ButtonDefaults.shapes(),
                modifier =
                    Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = failed
                    },
            ) {
                Text(stringResource(R.string.search_retry))
            }
        }
        return
    }
    val colors = MaterialTheme.colorScheme
    val text =
        when (results) {
            SearchResults.Idle -> ""
            SearchResults.Searching -> stringResource(R.string.search_searching)
            SearchResults.NoResults -> stringResource(R.string.search_no_results)
            is SearchResults.Found -> counterText(results)
            is SearchResults.Failed -> ""
        }
    Text(
        text = text,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (results is SearchResults.Found) colors.onSurface else colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        // Its own language's direction, as for user content: "1 of 50+" in English keeps its order in a right-to-left
        // layout, where the app has no translation.
        style =
            MaterialTheme.typography.labelLarge
                .copy(fontFeatureSettings = TABULAR_FIGURES)
                .userContent,
    )
}

@Composable
private fun counterText(found: SearchResults.Found): String =
    stringResource(
        if (found.isTotalApproximate) R.string.search_position_more else R.string.search_position,
        found.position,
        found.total,
    )

/**
 * The picked sender: their avatar and name in a selected [InputChip] with a close symbol, as the New group's people
 * picked; a tap removes them, and screen readers hear "Messages from Ada" with that action.
 */
@Composable
private fun SenderChip(
    sender: SearchSender,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = sender.name.resolve()
    val description = stringResource(R.string.search_sender_chip, name)
    val removeLabel = stringResource(R.string.search_sender_remove)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.userContent,
            )
        },
        shapes = InputChipDefaults.shapes(),
        modifier =
            modifier.semantics {
                contentDescription = description
                onClick(label = removeLabel) {
                    onRemove()
                    true
                }
            },
        avatar = { Avatar(photo = sender.photo, name = name, id = sender.id, size = InputChipDefaults.AvatarSize) },
        trailingIcon = {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}

/** An icon button of the bar, named by [label] for screen readers and in a [PlainTooltip], as in toolbars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BarButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(painter = painterResource(icon), contentDescription = label)
        }
    }
}

/** Tabular figures, so the counter does not change width as its numbers do. */
private const val TABULAR_FIGURES = "tnum"
