package dev.kyralabs.pingogram.feature.chat.ui.dates

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.DateTimeEvent
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A tapped date in a message (`docs/DESIGN_SYSTEM.md` §6 "Dates in messages"), a [ModalBottomSheet] as the
 * conversation's other sheets: the date in full in `titleLargeEmphasized` (a heading) over the reader's time zone in
 * `bodyMedium` `onSurfaceVariant`, then "Add to calendar" and "Copy" as list items inset like the chat list's rows.
 * Dragging it down, the scrim and back close it ([DateTimeEvent.Dismiss]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeBottomSheet(
    sheet: DateTimeSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(DateTimeEvent.Dismiss) }) {
        DateTimeContent(sheet, currentOnEvent)
    }
}

@Composable
internal fun DateTimeContent(
    sheet: DateTimeSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth().padding(bottom = spacing.large)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            Text(
                text = sheet.text,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLargeEmphasized.userContent,
            )
            Text(
                text = stringResource(R.string.date_time_zone, sheet.zoneName),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ActionRow(R.drawable.ic_calendar_month, R.string.date_time_add_to_calendar) {
            onEvent(DateTimeEvent.AddToCalendar)
        }
        ActionRow(UiR.drawable.ic_content_copy, R.string.date_time_copy) { onEvent(DateTimeEvent.Copy) }
    }
}

@Composable
private fun ActionRow(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    val insets = sheetRowInsets()
    ListItem(
        onClick = onClick,
        modifier = Modifier.padding(insets),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = insetContentPadding(insets),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
    ) {
        Text(stringResource(label))
    }
}

@Preview
@Composable
private fun DateTimeContentPreview() {
    PingogramTheme(dynamicColor = false) {
        DateTimeContent(
            DateTimeSheet(
                unixTime = 1_796_580_000,
                text = "Sunday, December 6, 2026, 6:00 PM",
                zoneName = "Central European Time",
            ),
            onEvent = {},
        )
    }
}
