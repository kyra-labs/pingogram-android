package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The list of an exceptions page: its rows, one segmented group under a count.

/** The rows under their count, keyed by chat, sliding on the motion scheme's springs as rows leave. */
@Composable
internal fun ExceptionsList(
    state: ExceptionsState.Content,
    onEvent: (NotificationExceptionsEvent) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    val style =
        SettingsListRowStyle(
            MaterialTheme.sizes.contentMaxWidth,
            spacing.windowMarginCompact,
            MaterialTheme.motionScheme,
        )
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + spacing.small,
                bottom = contentPadding.calculateBottomPadding() + spacing.extraLarge,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        // The gap between segmented rows; the heading and the end keep their own padding besides.
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = HEADER, contentType = HEADER) {
            SettingsSectionHeader(
                title =
                    pluralStringResource(
                        R.plurals.settings_exceptions_header,
                        state.items.size,
                        numbers.format(state.items.size),
                    ),
                modifier = Modifier.settingsListRow(this, style),
            )
        }
        itemsIndexed(state.items, key = { _, item -> item.chatId }, contentType = { _, _ -> ROW }) { index, item ->
            ExceptionRow(
                item = item,
                shapes = ListItemDefaults.segmentedShapes(index, state.items.size),
                onClick = { onEvent(NotificationExceptionsEvent.OpenMuteOptions(item.chatId)) },
                onRemove = { onEvent(NotificationExceptionsEvent.Change(item.chatId, MuteChange.UseDefault)) },
                modifier = Modifier.settingsListRow(this, style),
            )
        }
    }
}

/**
 * A chat of the list, a row of its segmented group ([shapes]): its avatar, title and whether it notifies, and a button
 * that removes the exception, or progress while a change is saved. The row opens the chat's mute options.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExceptionRow(
    item: NotificationExceptionItem,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        enabled = !item.isChanging,
        leadingContent = {
            Avatar(
                photo = item.photo,
                name = item.title,
                id = item.chatId,
                size = MaterialTheme.sizes.avatarSmall,
            )
        },
        trailingContent = {
            if (item.isChanging) {
                val changing = stringResource(R.string.settings_exception_changing, item.title)
                LoadingIndicator(Modifier.semantics { contentDescription = changing })
            } else {
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.settings_exception_remove, item.title),
                    )
                }
            }
        },
        supportingContent = { Text(item.status.resolve()) },
    ) {
        Text(item.title)
    }
}

private const val HEADER = "header"
private const val ROW = "row"
