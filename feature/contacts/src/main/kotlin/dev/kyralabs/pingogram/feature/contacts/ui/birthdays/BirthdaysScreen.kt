package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.SectionHeader
import dev.kyralabs.pingogram.feature.contacts.ui.preview.BirthdaysPreviewStates
import dev.kyralabs.pingogram.feature.contacts.ui.row
import dev.kyralabs.pingogram.feature.contacts.ui.rowInsets
import kotlinx.collections.immutable.ImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Birthdays page (`REQUIREMENTS.md` FR-X18, `docs/DESIGN_SYSTEM.md` §6 "Birthdays"), pushed above the Contacts
 * tab: a small [TopAppBar] with back ([onBack]) and the title on `surfaceContainer`, and below it on a rounded
 * `surface` sheet ([ContentSheet]) today's birthdays, the upcoming ones and those just past, each group under its
 * header as segmented rows. A row opens the chat ([BirthdaysEvent.Open]); its end holds "Remind me", a toggle
 * ([BirthdaysEvent.SetReminder]), and "Add to calendar" ([BirthdaysEvent.AddToCalendar]). While the names load the
 * sheet shows the delayed loading indicator; without a birthday, an empty state.
 */
@Composable
internal fun BirthdaysScreen(
    state: BirthdaysUiState,
    onEvent: (BirthdaysEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        // The sheet paints the top bar's container around itself (ContentSheet), so nothing lies under it.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = { BirthdaysTopBar(onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            val body = Modifier.fillMaxSize().padding(contentPadding)
            when (state) {
                BirthdaysUiState.Loading -> BirthdaysLoading(body)
                BirthdaysUiState.Empty -> NoBirthdays(body)
                is BirthdaysUiState.Content -> BirthdaysList(state, onEvent, contentPadding)
            }
        }
    }
}

/** The small top bar: back and "Birthdays" in `titleLargeEmphasized`, on the page's `surfaceContainer`. */
@Composable
private fun BirthdaysTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.birthdays_title),
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.birthdays_back),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}

/** The three groups in one `LazyColumn`, every entry keyed and typed, at most `contentMaxWidth` wide. */
@Composable
private fun BirthdaysList(
    content: BirthdaysUiState.Content,
    onEvent: (BirthdaysEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    val style = RowStyle(MaterialTheme.sizes.contentMaxWidth, MaterialTheme.motionScheme)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        group(R.string.birthdays_today, TODAY, content.today, onEvent, style)
        group(R.string.birthdays_upcoming, UPCOMING, content.upcoming, onEvent, style)
        group(R.string.birthdays_past, PAST, content.past, onEvent, style)
    }
}

/** How the list's entries sit: at most [maxWidth] wide, moving on [motionScheme]'s springs. */
private class RowStyle(
    val maxWidth: Dp,
    val motionScheme: MotionScheme,
)

/** A group of [items] under its header [title], left out while empty, each entry placed as [style] says. */
private fun LazyListScope.group(
    @StringRes title: Int,
    name: String,
    items: ImmutableList<BirthdayItem>,
    onEvent: (BirthdaysEvent) -> Unit,
    style: RowStyle,
) {
    if (items.isEmpty()) return
    item(key = "$name:$HEADER", contentType = HEADER) {
        SectionHeader(stringResource(title), Modifier.row(this, style.maxWidth, style.motionScheme))
    }
    itemsIndexed(items, key = { _, item -> "$name:${item.userId}" }, contentType = { _, _ -> BIRTHDAY_ROW }) {
        index,
        item,
        ->
        BirthdayRow(
            item = item,
            shapes = ListItemDefaults.segmentedShapes(index, items.size),
            onEvent = onEvent,
            modifier = Modifier.row(this, style.maxWidth, style.motionScheme).padding(rowInsets()),
        )
    }
}

/**
 * A birthday: the contact's small avatar and name over the date and the age they turn, then "Remind me", a filled
 * alarm while on, and "Add to calendar".
 */
@Composable
private fun BirthdayRow(
    item: BirthdayItem,
    shapes: ListItemShapes,
    onEvent: (BirthdaysEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = item.name.resolve()
    val dateText =
        item.turns?.let { pluralStringResource(R.plurals.birthdays_date_turns, it, item.date, it) } ?: item.date
    SegmentedListItem(
        onClick = { onEvent(BirthdaysEvent.Open(item.userId)) },
        shapes = shapes,
        modifier = modifier,
        leadingContent = {
            Avatar(photo = item.photo, name = name, id = item.userId, size = MaterialTheme.sizes.avatarSmall)
        },
        supportingContent = { Text(dateText, maxLines = 1) },
        trailingContent = { BirthdayActions(item, name, onEvent) },
        content = { Text(name, maxLines = 1) },
    )
}

/** "Remind me", a toggle with its state for screen readers, and "Add to calendar". */
@Composable
private fun BirthdayActions(
    item: BirthdayItem,
    name: String,
    onEvent: (BirthdaysEvent) -> Unit,
) {
    val remind = stringResource(R.string.birthdays_remind_me, name)
    val calendar = stringResource(R.string.birthdays_add_to_calendar, name)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconToggleButton(
            checked = item.remind,
            onCheckedChange = { onEvent(BirthdaysEvent.SetReminder(item.userId, it)) },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.semantics { contentDescription = remind },
        ) {
            Icon(
                painter = painterResource(if (item.remind) UiR.drawable.ic_alarm_filled else UiR.drawable.ic_alarm),
                contentDescription = null,
            )
        }
        IconButton(
            onClick = { onEvent(BirthdaysEvent.AddToCalendar(item.userId)) },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.semantics { contentDescription = calendar },
        ) { Icon(painterResource(UiR.drawable.ic_edit_calendar), contentDescription = null) }
    }
}

/** The delayed loading indicator in the middle of the sheet, named for screen readers. */
@Composable
private fun BirthdaysLoading(modifier: Modifier) {
    val description = stringResource(R.string.birthdays_loading)
    Box(modifier, contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** No birthday these days: an empty state on `MaterialShapes.Cookie9Sided` with the cake. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoBirthdays(modifier: Modifier) {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_cake_add,
        title = stringResource(R.string.birthdays_empty_title),
        body = stringResource(R.string.birthdays_empty_body),
        modifier = modifier,
    )
}

// Keys and content types of the list's entries; a row's key is its group's and its contact's.
private const val TODAY = "today"
private const val UPCOMING = "upcoming"
private const val PAST = "past"
private const val HEADER = "header"
private const val BIRTHDAY_ROW = "birthday"

@PingogramPreviews
@Composable
private fun BirthdaysScreenPreview(
    @PreviewParameter(BirthdaysPreviewStates::class) state: BirthdaysUiState,
) {
    PingogramPreview { BirthdaysScreen(state = state, onEvent = {}, onBack = {}) }
}
