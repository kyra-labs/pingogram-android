package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncControls
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEvent
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.formatPhoneNumber
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// Contacts sync on the Contacts tab (docs/DESIGN_SYSTEM.md §6, "Contacts sync"): its offer, its paused state, its
// status row, and the phone's contacts to invite.

/**
 * What the tab shows of contacts sync above the contacts, for [sync]: the offer or the paused state as a card
 * ([ContactSyncCard]), or the status row while it is on ([ContactSyncStatusRow]); nothing while it is hidden.
 */
@Composable
internal fun ContactSyncItem(
    sync: ContactSyncUi,
    controls: ContactSyncControls,
    onSyncEvent: (ContactSyncEvent) -> Unit,
    onDismissOffer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (sync) {
        ContactSyncUi.Hidden -> {
            // Settings still offers it.
        }

        ContactSyncUi.Offer -> {
            ContactSyncCard(
                title = R.string.contacts_sync_offer_title,
                body = R.string.contacts_sync_offer_body,
                secondary = R.string.contacts_sync_not_now,
                onSecondary = onDismissOffer,
                primary = R.string.contacts_sync_turn_on,
                onPrimary = { onSyncEvent(ContactSyncEvent.TurnOn) },
                modifier = modifier,
            )
        }

        ContactSyncUi.NeedsPermission -> {
            val deniedForGood = controls.permanentlyDenied
            val allow = if (deniedForGood) R.string.contacts_sync_open_settings else R.string.contacts_sync_allow
            ContactSyncCard(
                title = R.string.contacts_sync_paused_title,
                body = R.string.contacts_sync_paused_body,
                secondary = R.string.contacts_sync_turn_off,
                onSecondary = { onSyncEvent(ContactSyncEvent.TurnOff) },
                primary = allow,
                onPrimary = { onSyncEvent(ContactSyncEvent.Allow) },
                modifier = modifier,
            )
        }

        is ContactSyncUi.On -> {
            ContactSyncStatusRow(
                sync = sync,
                enabled = !controls.isWorking,
                onSyncNow = { onSyncEvent(ContactSyncEvent.SyncNow) },
                modifier = modifier,
            )
        }
    }
}

/**
 * A filled [Card] on `surfaceContainerHigh` at the top of the list, inset like the rows, as the chat list's
 * notification request (`docs/DESIGN_SYSTEM.md` §6): the contacts icon in `primary`, its [title] in `titleMedium`, its
 * [body] in `bodyMedium` `onSurfaceVariant`, then a [TextButton] ([secondary]) and a [Button] ([primary]) at the end,
 * both with the press morph.
 */
@Suppress("LongParameterList") // What the card says, and its two buttons.
@Composable
internal fun ContactSyncCard(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes secondary: Int,
    onSecondary: () -> Unit,
    @StringRes primary: Int,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Card(
        modifier = modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(start = spacing.large, top = spacing.large, end = spacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_contacts),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(title),
                    modifier = Modifier.padding(start = spacing.large).semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(body),
                modifier = Modifier.padding(top = spacing.small),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
        ) {
            TextButton(onClick = onSecondary, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(secondary), style = MaterialTheme.typography.labelLarge)
            }
            Button(onClick = onPrimary, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(primary), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/**
 * Contacts sync is on: a row like the actions above it, the contacts icon in a `primaryContainer` circle, "Synced with
 * your phone" and below it "Syncing…", when it last synced, or what failed in `error` (a polite live region), with a
 * "Sync now" [TextButton] at the end, waiting while a sync runs.
 */
@Composable
internal fun ContactSyncStatusRow(
    sync: ContactSyncUi.On,
    enabled: Boolean,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val supporting =
        when {
            sync.isSyncing -> stringResource(R.string.contacts_sync_syncing)
            sync.error != null -> stringResource(R.string.contacts_sync_failed, errorText(sync.error))
            else -> sync.lastSynced?.resolve() ?: stringResource(R.string.contacts_sync_never)
        }
    val insets = rowInsets()
    ListItem(
        modifier = modifier.padding(insets),
        contentPadding = insetContentPadding(insets),
        leadingContent = { SyncIcon() },
        supportingContent = {
            Text(
                text = supporting,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = with(MaterialTheme.colorScheme) { if (sync.error != null) error else onSurfaceVariant },
            )
        },
        trailingContent = {
            TextButton(onClick = onSyncNow, enabled = enabled && !sync.isSyncing, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.contacts_sync_now))
            }
        },
        colors = ListItemDefaults.colors(),
        content = { Text(stringResource(R.string.contacts_sync_on_title)) },
    )
}

/**
 * A contact of the phone no one uses Telegram with: the initials avatar, the name and the number (left to right), and
 * an "Invite" [TextButton] at the end ([onInvite]), named with the contact for screen readers.
 */
@Composable
internal fun InviteRow(
    item: InviteItem,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inviteLabel = stringResource(R.string.contacts_invite_person, item.name)
    val country = LocalConfiguration.current.locales[0].country
    val number = remember(item.phoneNumber, country) { formatPhoneNumber(item.phoneNumber, country) }
    val insets = rowInsets()
    ListItem(
        modifier = modifier.padding(insets),
        contentPadding = insetContentPadding(insets),
        leadingContent = {
            Avatar(
                photo = null,
                name = item.name,
                id = item.key.hashCode().toLong(),
                size = MaterialTheme.sizes.avatarRow,
            )
        },
        supportingContent = {
            Text(
                text = number,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium.numericContent,
            )
        },
        trailingContent = {
            TextButton(
                onClick = onInvite,
                modifier = Modifier.semantics { contentDescription = inviteLabel },
                shapes = ButtonDefaults.shapes(),
            ) {
                // The button's description names the contact; its text would only repeat "Invite".
                Text(stringResource(R.string.contacts_invite_action), Modifier.clearAndSetSemantics {})
            }
        },
        content = {
            Text(
                text = item.name,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium.userContent,
            )
        },
    )
}

/** The contacts icon in a `primaryContainer` circle the size of the avatars. */
@Composable
private fun SyncIcon() {
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.avatarRow)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_contacts),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@PingogramPreviews
@Composable
private fun ContactSyncItemsPreview() {
    PingogramPreview {
        Column {
            ContactSyncItem(ContactSyncUi.Offer, ContactSyncControls(), onSyncEvent = {}, onDismissOffer = {})
            ContactSyncItem(ContactSyncUi.NeedsPermission, ContactSyncControls(), onSyncEvent = {}, onDismissOffer = {})
            ContactSyncItem(
                ContactSyncUi.On(isSyncing = false, lastSynced = null, error = null),
                ContactSyncControls(),
                onSyncEvent = {},
                onDismissOffer = {},
            )
            InviteRow(InviteItem("1:+15550100", "Bob Example", "+15550100"), onInvite = {})
        }
    }
}
