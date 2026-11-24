package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.contacts.R
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The key and content type of the Contacts tab's Birthdays row. */
internal const val BIRTHDAYS_ROW = "birthdays"

/**
 * The Contacts tab's "Birthdays" row (`REQUIREMENTS.md` FR-X18, `docs/DESIGN_SYSTEM.md` §6 "Birthdays"): the cake on a
 * `MaterialShapes.Cookie9Sided` badge in `tertiaryContainer`, "Birthdays" over who has one these days ("Ada Lovelace,
 * Grace Hopper and 1 more"), and a chevron; a tap opens the page ([onClick]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BirthdaysRow(
    entry: BirthdaysEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        modifier = modifier,
        leadingContent = {
            Box(
                Modifier
                    .size(MaterialTheme.sizes.avatarSmall)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_cake_add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        },
        supportingContent = { Text(whoText(entry), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Icon(painterResource(UiR.drawable.ic_chevron_right), contentDescription = null) },
        content = { Text(stringResource(R.string.birthdays_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** "Ada", "Ada and Grace", or "Ada, Grace and 2 more". */
@Composable
private fun whoText(entry: BirthdaysEntry): String {
    val first = entry.names.first().resolve()
    val second = entry.names.getOrNull(1)?.resolve()
    return when {
        second == null -> first
        entry.more <= 0 -> stringResource(R.string.birthdays_row_two, first, second)
        else -> pluralStringResource(R.plurals.birthdays_row_more, entry.more, first, second, entry.more)
    }
}

@PingogramPreviews
@Composable
private fun BirthdaysRowPreview() {
    PingogramPreview {
        BirthdaysRow(
            entry = BirthdaysEntry(persistentListOf(UiText.Plain("Ada Lovelace"), UiText.Plain("Grace Hopper")), 1),
            onClick = {},
        )
    }
}
