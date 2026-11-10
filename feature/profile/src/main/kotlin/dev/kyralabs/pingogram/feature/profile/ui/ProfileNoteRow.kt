package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The note about a contact (`docs/DESIGN_SYSTEM.md` §6 "Contact note", the owner's board 15a): its text (user content),
 * or "Add a note" in `onSurfaceVariant` without one, over "Note" in `primary` and the emphasized weight, a lock and
 * "Only you see this"; at the end the edit symbol in a `secondaryContainer` circle, decorative, as the whole row edits.
 * The row's container takes an 8% tint of `primary`, so the private note stands apart from what the contact shares.
 * Screen readers hear it as one row whose click edits the note.
 */
@Composable
internal fun NoteRow(
    note: ContactNote,
    shapes: ListItemShapes,
    onEvent: (ProfileEvent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val editLabel = stringResource(R.string.profile_note_edit)
    SegmentedListItem(
        onClick = { onEvent(ProfileEvent.EditNote) },
        modifier = Modifier.semantics { onClick(label = editLabel, action = null) },
        shapes = shapes,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = colors.primary.copy(alpha = NOTE_TINT).compositeOver(profileRowContainerColor()),
            ),
        supportingContent = { NotePrivacyLine() },
        trailingContent = { NoteEditSymbol() },
    ) {
        if (note.text.isBlank()) {
            Text(stringResource(R.string.profile_note_add), color = colors.onSurfaceVariant)
        } else {
            Text(note.text, style = MaterialTheme.typography.bodyLarge.userContent)
        }
    }
}

/** "Note · (lock) Only you see this", "Note" in `primary` and the emphasized weight. */
@Composable
private fun NotePrivacyLine() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Text(
            stringResource(R.string.profile_note),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = MaterialTheme.typography.labelLargeEmphasized.fontWeight,
        )
        Text(stringResource(R.string.profile_note_separator))
        Icon(
            painter = painterResource(UiR.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
        )
        Text(stringResource(R.string.profile_note_private))
    }
}

/** The edit symbol in a `secondaryContainer` circle; decorative, as the whole row edits. */
@Composable
private fun NoteEditSymbol() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.leadingIconContainer)
                .background(colors.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_edit),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            tint = colors.onSecondaryContainer,
        )
    }
}

/** How much of `primary` the note row's container takes. */
private const val NOTE_TINT = 0.08f
