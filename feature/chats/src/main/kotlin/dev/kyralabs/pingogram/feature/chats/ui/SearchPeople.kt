package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.animateItem
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import java.util.Locale

// The top people of search's suggestions (SearchResults.kt), above the recent chats.

/**
 * The people the user writes to most, side by side: each an avatar over the name, opening the chat with them
 * ([onOpen]); a button for screen readers, named by the person.
 */
@Composable
internal fun TopPeople(
    people: ImmutableList<SearchChatItem>,
    onOpen: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    // People who leave as the words narrow them fade out and the others slide into their places.
    val motion = rememberItemMotion()
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = spacing.small, vertical = spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        items(people, key = { it.id }) { person ->
            Person(person, onClick = { onOpen(person.id) }, Modifier.animateItem(this, motion))
        }
    }
}

@Composable
private fun Person(
    item: SearchChatItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val sizes = MaterialTheme.sizes
    val title = item.title.resolve()
    val description = chatDescription(item, title, subtitle = null)
    Column(
        modifier =
            modifier
                .width(sizes.avatarMedium + spacing.extraLarge)
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = spacing.small)
                .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(
            photo = item.photo,
            name = title,
            id = item.id,
            size = sizes.avatarMedium,
            isOnline = item.isOnline,
            placeholderIcon = item.placeholderIcon,
            onlineRingColor = rowContainerColor,
            shape = item.avatarShape,
        )
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = spacing.extraSmall),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            // A second line at large font scales, where one line of the column's width cuts most names.
            maxLines = if (isLargeFontScale()) 2 else 1,
            style = MaterialTheme.typography.labelMedium.userContent,
        )
    }
}

/**
 * The top people in the search header, above the chips (`docs/DESIGN_SYSTEM.md` §13.4): the [people] the view model
 * narrowed to the query, growing in and shrinking away on the motion scheme's springs as matches come and go.
 */
@Composable
internal fun HeaderPeople(
    people: ImmutableList<SearchChatItem>,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    // The last people shown stay while the row shrinks away: kept, not state, so keeping them recomposes nothing.
    val last = remember { LastPeople(people) }
    if (people.isNotEmpty()) last.people = people
    val shown = last.people
    AnimatedVisibility(
        visible = people.isNotEmpty(),
        modifier = modifier,
        enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(motionScheme.defaultSpatialSpec()) + fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        TopPeople(shown, { onEvent(SearchEvent.OpenChat(it)) })
    }
}

/** The people [HeaderPeople] showed last. */
private class LastPeople(
    var people: ImmutableList<SearchChatItem>,
)

/**
 * Whether this name has a word starting with each word of [query], ignoring case; true for a blank query. How the
 * header narrows the top people as the user types (§13.4).
 */
internal fun String.matchesWords(
    query: String,
    locale: Locale,
): Boolean {
    val typed = query.lowercase(locale).split(WHITESPACE).filter { it.isNotEmpty() }
    val words = lowercase(locale).split(WHITESPACE)
    return typed.all { word -> words.any { it.startsWith(word) } }
}

private val WHITESPACE = Regex("\\s+")
