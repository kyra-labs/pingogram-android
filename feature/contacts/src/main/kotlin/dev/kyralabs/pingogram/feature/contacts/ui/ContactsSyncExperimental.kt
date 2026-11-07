package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncControls
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEvent
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

// The experimental look's entries above the contacts and contacts sync's offer (docs/DESIGN_SYSTEM.md §13.8).

/**
 * The experimental look's entries above the contacts (`docs/DESIGN_SYSTEM.md` §13.8): contacts sync (its offer as
 * [ExperimentalSyncOffer], its other states as in the standard look), then "Online now" ([OnlineNow]) while someone
 * is. "Add contact" sits beside the navigation bar and "Invite" under the header instead of the action rows.
 */
internal fun LazyListScope.experimentalItems(
    state: ContactsUiState,
    syncControls: ContactSyncControls,
    callbacks: ContactsCallbacks,
    maxWidth: Dp,
    motionScheme: MotionScheme,
) {
    val sync = state.sync
    val online = (state.content as? ContactsContent.Contacts)?.online ?: persistentListOf()
    if (sync != ContactSyncUi.Hidden) {
        item(key = SYNC_ROW, contentType = SYNC_ROW) {
            val modifier = Modifier.row(this, maxWidth, motionScheme)
            if (sync == ContactSyncUi.Offer) {
                ExperimentalSyncOffer(
                    onTurnOn = { callbacks.onSyncEvent(ContactSyncEvent.TurnOn) },
                    onDismiss = { callbacks.onEvent(ContactsEvent.DismissSyncOffer) },
                    modifier = modifier,
                )
            } else {
                ContactSyncItem(
                    sync = sync,
                    controls = syncControls,
                    onSyncEvent = callbacks.onSyncEvent,
                    onDismissOffer = { callbacks.onEvent(ContactsEvent.DismissSyncOffer) },
                    modifier = modifier,
                )
            }
        }
    }
    if (online.isNotEmpty()) {
        item(key = ONLINE_ROW, contentType = ONLINE_ROW) {
            OnlineNow(
                contacts = online,
                onOpen = { callbacks.onEvent(ContactsEvent.OpenChat(it)) },
                modifier = Modifier.row(this, maxWidth, motionScheme),
            )
        }
    }
}

/**
 * The offer of contacts sync in the experimental look: a filled `Card` on `surfaceContainerHigh` with `extraLarge`
 * corners, inset like the rows: the contacts icon on a `MaterialShapes.Flower` badge in `tertiaryContainer`, the title
 * in `titleSmallEmphasized` over the text in `bodyMedium` `onSurfaceVariant`, "Turn on" as a filled `Button`
 * ([onTurnOn]) and a dismiss `IconButton` ("Not now", [onDismiss]).
 */
@Composable
internal fun ExperimentalSyncOffer(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
    ) {
        // From 150% font the buttons go under the text, which needs the room.
        if (LocalDensity.current.fontScale < LARGE_FONT_SCALE) {
            Row(
                modifier = Modifier.padding(start = spacing.medium, top = spacing.medium, bottom = spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowerBadge()
                SyncOfferText(Modifier.weight(1f).padding(horizontal = spacing.medium))
                SyncOfferButtons(onTurnOn, onDismiss)
            }
        } else {
            Column(Modifier.padding(start = spacing.medium, top = spacing.medium, bottom = spacing.small)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlowerBadge()
                    SyncOfferText(Modifier.weight(1f).padding(horizontal = spacing.medium))
                }
                Row(Modifier.align(Alignment.End).padding(top = spacing.small)) {
                    SyncOfferButtons(onTurnOn, onDismiss)
                }
            }
        }
    }
}

/** The contacts icon on a `MaterialShapes.Flower` badge in `tertiaryContainer`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlowerBadge() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.leadingIconContainer)
                .background(colors.tertiaryContainer, MaterialShapes.Flower.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_contacts),
            contentDescription = null,
            tint = colors.onTertiaryContainer,
        )
    }
}

/** The offer's title over its text. */
@Composable
private fun SyncOfferText(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            text = stringResource(R.string.contacts_sync_offer_title),
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        Text(
            text = stringResource(R.string.contacts_sync_offer_body),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** "Turn on" and the dismiss button. */
@Composable
private fun SyncOfferButtons(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
) {
    Button(onClick = onTurnOn, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(R.string.contacts_sync_turn_on), style = MaterialTheme.typography.labelLargeEmphasized)
    }
    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(UiR.drawable.ic_close),
            contentDescription = stringResource(R.string.contacts_sync_not_now),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PingogramPreviews
@Composable
private fun ExperimentalSyncOfferPreview() {
    PingogramPreview { ExperimentalSyncOffer(onTurnOn = {}, onDismiss = {}) }
}
