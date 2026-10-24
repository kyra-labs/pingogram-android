package dev.kyralabs.pingogram.feature.chat.ui.poll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.PollEvent
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.preview.PollPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.scheduledTime
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets
import java.text.NumberFormat

/**
 * Who voted for what in a public poll (`docs/DESIGN_SYSTEM.md` §6 "Poll voters"), a [ModalBottomSheet] as the
 * conversation's other sheets: "Votes" in `titleLargeEmphasized` over the question, then a section per answer someone
 * chose: its text in `titleSmallEmphasized` `primary` with its votes, its voters as list items inset like the chat
 * list's rows (40 dp avatar, the name as user content, when they voted), and below them a [LoadingIndicator] while a
 * page loads, why the last one failed with "Try again", or "Show more" ([PollEvent.LoadMoreVoters]). A tapped voter
 * closes the sheet and opens their profile ([PollEvent.OpenVoter]); dragging it down, the scrim and back close it
 * ([PollEvent.DismissVoters]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PollVotersBottomSheet(
    sheet: PollVotersSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(PollEvent.DismissVoters) }) {
        PollVotersContent(sheet, currentOnEvent)
    }
}

/** The title, the question and the sections. */
@Composable
internal fun PollVotersContent(
    sheet: PollVotersSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    LazyColumn(
        modifier = Modifier.testTag(POLL_VOTERS_TEST_TAG),
        contentPadding = PaddingValues(bottom = spacing.large),
    ) {
        item(key = KEY_TITLE, contentType = CONTENT_TYPE_TITLE) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
            ) {
                Text(
                    text = stringResource(R.string.poll_voters_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = sheet.question,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.userContent,
                )
            }
        }
        if (sheet.sections.isEmpty()) {
            item(
                key = KEY_NONE,
                contentType = CONTENT_TYPE_NOTE,
            ) { SheetNote(stringResource(R.string.content_poll_no_votes)) }
        }
        sheet.sections.forEach { section -> section(section, onEvent) }
    }
}

/** One answer's header, its voters, and what follows them: a [LoadingIndicator], the failure, or "Show more". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator
private fun LazyListScope.section(
    section: PollVoterSection,
    onEvent: (ChatEvent) -> Unit,
) {
    val index = section.optionIndex
    item(key = "header:$index", contentType = CONTENT_TYPE_HEADER) { SectionHeader(section) }
    items(section.voters, key = { "voter:$index:${it.sender}" }, contentType = { CONTENT_TYPE_VOTER }) { voter ->
        VoterRow(voter, onClick = { onEvent(PollEvent.OpenVoter(voter.sender)) })
    }
    when {
        section.isLoading -> {
            item(key = "loading:$index", contentType = CONTENT_TYPE_LOADING) {
                Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }

        section.error != null -> {
            item(key = "error:$index", contentType = CONTENT_TYPE_ERROR) {
                SectionFailed(onRetry = { onEvent(PollEvent.LoadMoreVoters(index)) })
            }
        }

        section.hasMore -> {
            item(key = "more:$index", contentType = CONTENT_TYPE_MORE) {
                Box(Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.small)) {
                    TextButton(
                        onClick = { onEvent(PollEvent.LoadMoreVoters(index)) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.poll_voters_more))
                    }
                }
            }
        }
    }
}

/** The answer's text (user content) and its votes, a heading for screen readers. */
@Composable
private fun SectionHeader(section: PollVoterSection) {
    val number = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(section.count)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.large,
                    end = MaterialTheme.spacing.large,
                    top = MaterialTheme.spacing.large,
                    bottom = MaterialTheme.spacing.small,
                ).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.text,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmallEmphasized.userContent,
        )
        Text(
            text = pluralStringResource(R.plurals.content_poll_votes, section.count, number),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** A voter: avatar, name and when they voted, on the sheet's color, inset like the chat list's rows. */
@Composable
private fun VoterRow(
    voter: PollVoterRow,
    onClick: () -> Unit,
) {
    val name = voter.name.resolve()
    val insets = sheetRowInsets()
    val context = LocalContext.current
    ListItem(
        onClick = onClick,
        modifier = Modifier.padding(insets),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = insetContentPadding(insets),
        leadingContent = {
            Avatar(
                photo = voter.photo,
                name = name,
                id = voter.id,
                size = MaterialTheme.sizes.avatarSmall,
                placeholderIcon = voter.placeholderIcon,
            )
        },
        supportingContent =
            voter.date.takeIf { it > 0 }?.let { date ->
                { Text(text = context.scheduledTime(date), style = LocalTextStyle.current.userContent) }
            },
    ) {
        Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.userContent)
    }
}

/** That this answer's voters could not be listed, and "Try again". */
@Composable
private fun SectionFailed(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.poll_voters_error),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.poll_voters_retry))
        }
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.extraLarge),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private const val KEY_TITLE = "title"
private const val KEY_NONE = "none"
private const val CONTENT_TYPE_TITLE = "title"
private const val CONTENT_TYPE_NOTE = "note"
private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_VOTER = "voter"
private const val CONTENT_TYPE_LOADING = "loading"
private const val CONTENT_TYPE_ERROR = "error"
private const val CONTENT_TYPE_MORE = "more"

/** The sheet's list, for UI tests. */
internal const val POLL_VOTERS_TEST_TAG = "poll_voters"

@PingogramPreviews
@Composable
private fun PollVotersPreview() {
    PingogramPreview { PollVotersContent(PollPreviewData.voters, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun PollVotersLoadingPreview() {
    PingogramPreview { PollVotersContent(PollPreviewData.votersLoading, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun PollVotersFailedPreview() {
    PingogramPreview { PollVotersContent(PollPreviewData.votersFailed, onEvent = {}) }
}
