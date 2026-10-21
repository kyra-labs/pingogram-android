package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.navigation.ContactsRoute
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.ScrollToTopOnReselect
import dev.kyralabs.pingogram.core.ui.component.screenContentWindowInsets
import dev.kyralabs.pingogram.core.ui.component.sidesOnly
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncControls
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncEvent
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewStates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

// The entries of the Contacts tab's list (ContactsList): its action rows, the contacts, the phone's contacts to invite.

/** "Add contact", "Invite friends" and, unless [sync] is hidden, contacts sync's card or status row. */
internal fun LazyListScope.actionItems(
    sync: ContactSyncUi,
    syncControls: ContactSyncControls,
    callbacks: ContactsCallbacks,
    maxWidth: Dp,
    motionScheme: MotionScheme,
) {
    item(key = ADD_ROW, contentType = ACTION_ROW) {
        ContactsActionRow(
            icon = UiR.drawable.ic_add,
            label = stringResource(R.string.contacts_add),
            onClick = callbacks.onAddContact,
            modifier = Modifier.row(this, maxWidth, motionScheme),
        )
    }
    item(key = INVITE_ROW, contentType = ACTION_ROW) {
        ContactsActionRow(
            icon = UiR.drawable.ic_share,
            label = stringResource(R.string.contacts_invite),
            onClick = { callbacks.onEvent(ContactsEvent.Invite) },
            modifier = Modifier.row(this, maxWidth, motionScheme),
        )
    }
    if (sync != ContactSyncUi.Hidden) {
        item(key = SYNC_ROW, contentType = SYNC_ROW) {
            ContactSyncItem(
                sync = sync,
                controls = syncControls,
                onSyncEvent = callbacks.onSyncEvent,
                onDismissOffer = { callbacks.onEvent(ContactsEvent.DismissSyncOffer) },
                modifier = Modifier.row(this, maxWidth, motionScheme),
            )
        }
    }
}

/** The phone's contacts not on Telegram under a sticky "Invite to Telegram" header, if there are any. */
internal fun LazyListScope.inviteItems(
    invites: ImmutableList<InviteItem>,
    onEvent: (ContactsEvent) -> Unit,
    maxWidth: Dp,
    motionScheme: MotionScheme,
) {
    if (invites.isEmpty()) return
    stickyHeader(key = INVITES_HEADER, contentType = HEADER) {
        SectionHeader(
            stringResource(R.string.contacts_invite_section),
            Modifier.widthIn(max = maxWidth).fillMaxWidth(),
        )
    }
    items(invites, key = { "$INVITE:${it.key}" }, contentType = { INVITE }) { invite ->
        InviteRow(
            item = invite,
            onInvite = { onEvent(ContactsEvent.InvitePhoneContact(invite.phoneNumber)) },
            modifier = Modifier.row(this, maxWidth, motionScheme),
        )
    }
}

/** The rows of [contacts], keyed by user id. */
internal fun LazyListScope.contactItems(
    contacts: ImmutableList<ContactItem>,
    onEvent: (ContactsEvent) -> Unit,
    onEditContact: (userId: Long) -> Unit,
    maxWidth: Dp,
    motionScheme: MotionScheme,
) {
    items(contacts, key = { it.userId }, contentType = { CONTACT_ROW }) { contact ->
        ContactRow(
            item = contact,
            onClick = { onEvent(ContactsEvent.OpenChat(contact.userId)) },
            onEdit = { onEditContact(contact.userId) },
            onDelete = { onEvent(ContactsEvent.Delete(contact.userId)) },
            modifier = Modifier.row(this, maxWidth, motionScheme),
        )
    }
}

/** The list index of each section's header: after the [leading] rows, each section's header and its contacts. */
internal fun headerIndices(
    sections: ImmutableList<ContactSection>,
    leading: Int,
): ImmutableList<Int> =
    sections
        .runningFold(leading) { index, section -> index + 1 + section.contacts.size }
        .dropLast(1)
        .toImmutableList()

/** A letter's header: `titleSmallEmphasized` in `primary` on the sheet's `surface`, so rows scroll under it. */
@Composable
internal fun SectionHeader(
    letter: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = letter,
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small)
                .semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}

/** A row of [scope] at most [maxWidth] wide, animating as the list changes on the [motionScheme]'s springs. */
internal fun Modifier.row(
    scope: LazyItemScope,
    maxWidth: Dp,
    motionScheme: MotionScheme,
): Modifier =
    with(scope) {
        this@row
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .animateItem(
                fadeInSpec = motionScheme.defaultEffectsSpec(),
                placementSpec = motionScheme.defaultSpatialSpec(),
                fadeOutSpec = motionScheme.fastEffectsSpec(),
            )
    }

/**
 * How far a row's container sits in from its place in the list, as the chat list's: [MaterialTheme.spacing]'s small
 * step from the sides of the sheet, and half of Material's gap between segmented list items from the rows around.
 */
@Composable
internal fun rowInsets(): PaddingValues =
    PaddingValues(horizontal = MaterialTheme.spacing.small, vertical = ListItemDefaults.SegmentedGap / 2)

/** The list item's content padding less [insets], so a row's content stays where the list item puts it. */
internal fun insetContentPadding(insets: PaddingValues): PaddingValues {
    val padding = ListItemDefaults.ContentPadding
    val direction = LayoutDirection.Ltr
    return PaddingValues(
        start = padding.calculateStartPadding(direction) - insets.calculateStartPadding(direction),
        top = padding.calculateTopPadding() - insets.calculateTopPadding(),
        end = padding.calculateEndPadding(direction) - insets.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding() - insets.calculateBottomPadding(),
    )
}

// Keys and content types of the list's entries; a contact's key is its user id.
internal const val ACTION_ROW = "action"
internal const val ADD_ROW = "action:add"
internal const val INVITE_ROW = "action:invite"
internal const val ACTION_ROWS = 2
internal const val SYNC_ROW = "sync"
internal const val INVITES_HEADER = "header:invites"
internal const val INVITE = "invite"
internal const val HEADER = "header"
internal const val NO_RESULTS = "noResults"
internal const val CONTACT_ROW = "contact"
