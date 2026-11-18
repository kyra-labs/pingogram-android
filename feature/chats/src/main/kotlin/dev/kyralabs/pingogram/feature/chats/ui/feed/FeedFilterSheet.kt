package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.isLargeFontScale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The feed's filter (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 4): "Filter" with Reset, "All channels · 14" /
 * "Chosen · 4" as a single-choice segmented row, the [channels] as checkbox rows (enabled with "Chosen"), "Only unread"
 * as a switch row, then "Mark all as read" (asking first from [MARK_ALL_CONFIRM_AT] posts) and "Show N posts", which
 * applies the choice. Nothing applies until then; [filter] is where the choice starts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedFilterSheet(
    channels: ImmutableList<FeedChannel>,
    filter: FeedFilter,
    onEvent: (FeedEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(filter) }
    var confirming by remember { mutableStateOf(false) }
    val unread = channels.postCount(draft.copy(onlyUnread = true))
    val markAll = {
        confirming = false
        onEvent(FeedEvent.MarkAllAsRead(draft.chosen))
    }
    PingogramBottomSheet(onDismissRequest = onDismiss) {
        FilterHeader(onReset = { draft = FeedFilter() })
        ChannelChoice(channels, draft) { draft = it }
        ChannelRows(channels, draft, Modifier.weight(1f, fill = false)) { draft = it }
        ListItem(
            modifier =
                Modifier.toggleable(draft.onlyUnread, role = Role.Switch) { draft = draft.copy(onlyUnread = it) },
            trailingContent = { CheckSwitch(checked = draft.onlyUnread) },
        ) { Text(stringResource(R.string.feed_filter_only_unread)) }
        FilterButtons(
            count = channels.postCount(draft),
            canMarkAll = unread > 0,
            onMarkAll = { if (unread >= MARK_ALL_CONFIRM_AT) confirming = true else markAll() },
            onShow = {
                onEvent(FeedEvent.SetFilter(draft))
                onDismiss()
            },
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(pluralStringResource(R.plurals.feed_mark_all_title, unread, unread)) },
            text = { Text(stringResource(R.string.feed_mark_all_body)) },
            confirmButton = { TextButton(onClick = markAll) { Text(stringResource(R.string.feed_action_mark_read)) } },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.feed_mark_all_cancel)) }
            },
        )
    }
}

/** "Filter" in `titleLargeEmphasized`, a heading, with Reset at its end. */
@Composable
private fun FilterHeader(onReset: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(
                start = MaterialTheme.spacing.large,
                end = MaterialTheme.spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feed_filter),
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        TextButton(onClick = onReset) { Text(stringResource(R.string.feed_filter_reset)) }
    }
}

/** "All channels · 14" or "Chosen · 4"; choosing starts from every channel, to leave some out. */
@Composable
private fun ChannelChoice(
    channels: ImmutableList<FeedChannel>,
    draft: FeedFilter,
    onChange: (FeedFilter) -> Unit,
) {
    val options =
        listOf(
            stringResource(R.string.feed_filter_all, channels.size),
            stringResource(R.string.feed_filter_chosen, draft.chosen?.size ?: channels.size),
        )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large)) {
        options.forEachIndexed { index, label ->
            val chosen = index == 1
            SegmentedButton(
                selected = (draft.chosen != null) == chosen,
                onClick = {
                    val ids = if (chosen) draft.chosen ?: channels.map(FeedChannel::chatId).toImmutableSet() else null
                    onChange(draft.copy(chosen = ids))
                },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

/** The channels as checkbox rows with their `Cookie6Sided` avatars, which only "Chosen" enables. */
@Composable
private fun ChannelRows(
    channels: ImmutableList<FeedChannel>,
    draft: FeedFilter,
    modifier: Modifier,
    onChange: (FeedFilter) -> Unit,
) {
    val chosen = draft.chosen
    LazyColumn(modifier.padding(vertical = MaterialTheme.spacing.small)) {
        items(channels, key = FeedChannel::chatId) { channel ->
            val checked = chosen == null || channel.chatId in chosen
            ListItem(
                modifier =
                    Modifier.toggleable(checked, enabled = chosen != null, role = Role.Checkbox) { on ->
                        val ids = chosen.orEmpty()
                        onChange(
                            draft.copy(
                                chosen = (if (on) ids + channel.chatId else ids - channel.chatId).toImmutableSet(),
                            ),
                        )
                    },
                leadingContent = {
                    Avatar(
                        photo = channel.photo,
                        name = channel.title,
                        id = channel.chatId,
                        size = MaterialTheme.sizes.avatarSmall,
                        shape = AvatarShape.Channel,
                    )
                },
                trailingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = chosen != null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) { Text(channel.title, maxLines = 2, style = MaterialTheme.typography.bodyLarge.userContent) }
        }
    }
}

/** "Mark all as read" and "Show N posts", side by side, or stacked at large font scales. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.FilterButtons(
    count: Int,
    canMarkAll: Boolean,
    onMarkAll: () -> Unit,
    onShow: () -> Unit,
) {
    val markAll: @Composable (Modifier) -> Unit = { modifier ->
        FilledTonalButton(
            onClick = onMarkAll,
            modifier = modifier,
            enabled = canMarkAll,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.feed_filter_mark_all))
        }
    }
    val show: @Composable (Modifier) -> Unit = { modifier ->
        Button(onClick = onShow, modifier = modifier, shapes = ButtonDefaults.shapes()) {
            Text(pluralStringResource(R.plurals.feed_filter_show, count, count))
        }
    }
    val padding = Modifier.padding(MaterialTheme.spacing.large)
    if (isLargeFontScale()) {
        Column(padding.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            markAll(Modifier.fillMaxWidth())
            show(Modifier.fillMaxWidth())
        }
    } else {
        Row(padding.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            markAll(Modifier.weight(1f))
            show(Modifier.weight(1f))
        }
    }
}

/** "Mark all as read" asks first from this many unread posts (the board's frame 4). */
internal const val MARK_ALL_CONFIRM_AT = 20
