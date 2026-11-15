package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The card about someone not in the contacts who wrote first (the owner's board 24c), above the profile's actions: the
 * add-person symbol in `primary`, "Dev isn't in your contacts" in `titleSmallEmphasized` over how many groups the two
 * share in `bodyMedium` `onSurfaceVariant`, one item for screen readers, read before the actions; then "Block" (an
 * `error` `TextButton`, after the profile's question) and "Report" (the report sheet) at the start, and "Add contact"
 * (a `FilledTonalButton`, the contact editor, only with [onAddContact]) at the end, wrapping onto two rows where they
 * do not fit. On the rows' container (`surface`, `surfaceContainer` with AMOLED black) with `large` corners.
 */
@Composable
internal fun ProfileStrangerCard(
    stranger: ProfileStranger,
    actions: ProfileActions,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
    onAddContact: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = profileRowContainerColor()),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_person_add),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_stranger_title, stranger.firstName.bidiIsolated()),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                    Text(
                        text = groupsInCommonText(stranger.groupsInCommon),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StrangerButtons(actions, onEvent, onAddContact)
        }
    }
}

@Composable
private fun StrangerButtons(
    actions: ProfileActions,
    onEvent: (ProfileEvent) -> Unit,
    onAddContact: (() -> Unit)?,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (actions.isBlocked == false) {
            TextButton(
                onClick = { onEvent(ProfileEvent.Block) },
                enabled = !actions.isChangingBlock,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.profile_stranger_block), color = MaterialTheme.colorScheme.error)
            }
        }
        if (actions.canReport) {
            TextButton(onClick = { onEvent(ProfileEvent.Report) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.profile_report))
            }
        }
        if (onAddContact != null) {
            FilledTonalButton(onClick = onAddContact, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.profile_stranger_add_contact))
            }
        }
    }
}

@Composable
private fun groupsInCommonText(count: Int): String =
    if (count == 0) {
        stringResource(R.string.profile_stranger_no_groups)
    } else {
        pluralStringResource(R.plurals.profile_stranger_groups, count, count)
    }

/**
 * The phone number row of someone whose privacy settings hide it (the owner's board 24c): "Hidden by Dev's privacy
 * settings" in `bodyLarge` `onSurfaceVariant` over the row's label, instead of leaving the row out.
 */
@Composable
internal fun HiddenPhoneRow(
    firstName: String,
    shapes: ListItemShapes,
) {
    SegmentedListItem(
        shapes = shapes,
        colors = profileRowColors(),
        supportingContent = { Text(stringResource(R.string.profile_phone)) },
    ) {
        Text(
            text = stringResource(R.string.profile_phone_hidden, firstName.bidiIsolated()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
