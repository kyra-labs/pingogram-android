package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import kotlinx.collections.immutable.ImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

// The Contacts tab's header parts, "Online now" and the letters' headers (docs/DESIGN_SYSTEM.md §13.8).

/**
 * The header's status line while expanded: "248 on Telegram · 12 online", the second part in `pingogramColors.online`
 * and the emphasized weight, left out while nobody is online; one text for screen readers.
 */
@Composable
internal fun ContactsStatusLine(
    count: Int,
    onlineCount: Int,
) {
    val resources = LocalResources.current
    val online = MaterialTheme.pingogramColors.online
    val weight = MaterialTheme.typography.bodyMediumEmphasized.fontWeight
    val text =
        remember(count, onlineCount, online, weight, resources) {
            buildAnnotatedString {
                append(resources.getQuantityString(R.plurals.contacts_count, count, count))
                if (onlineCount > 0) {
                    append(resources.getString(R.string.contacts_status_separator))
                    withStyle(SpanStyle(color = online, fontWeight = weight)) {
                        append(resources.getQuantityString(R.plurals.contacts_online_count, onlineCount, onlineCount))
                    }
                }
            }
        }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * Invite, My QR and Scan under the header, in place of the header's QR buttons and the list's "Invite friends": a
 * connected group of `FilledTonalButton`s sharing the width (`ButtonGroupDefaults` connected shapes with their press
 * morph, `ConnectedSpaceBetween` apart), each its icon and label, inside the window margin. It folds away with the
 * header as the list scrolls ([headerState]'s collapsed fraction, read in the layout and draw phases).
 */
@Composable
internal fun ContactActionGroup(
    headerState: TopAppBarState,
    onInvite: () -> Unit,
    onOpenMyQrCode: () -> Unit,
    onScanQrCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.contacts_actions)
    Row(
        modifier =
            modifier
                .collapsingWith(headerState)
                .fillMaxWidth()
                .padding(horizontal = windowMargin())
                .padding(bottom = MaterialTheme.spacing.large)
                .semantics { paneTitle = description },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val actions =
            listOf(
                Triple(UiR.drawable.ic_share, R.string.contacts_action_invite, onInvite),
                Triple(UiR.drawable.ic_qr_code, R.string.contacts_action_my_qr, onOpenMyQrCode),
                Triple(UiR.drawable.ic_qr_code_scanner, R.string.contacts_action_scan, onScanQrCode),
            )
        actions.forEachIndexed { index, (icon, label, onClick) ->
            GroupButton(icon, label, connectedShapes(index, actions.size), onClick, Modifier.weight(1f))
        }
    }
}

/** A button of [ContactActionGroup]: [icon] before [label] on one line. */
@Composable
private fun GroupButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    shapes: ButtonShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // From 150% font the labels need the icons' room.
    val showsIcon = LocalDensity.current.fontScale < LARGE_FONT_SCALE
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        shapes = shapes,
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small),
    ) {
        if (showsIcon) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        Text(
            text = stringResource(label),
            modifier = if (showsIcon) Modifier.padding(start = ButtonDefaults.IconSpacing) else Modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** The connected shapes of the button at [index] of [count]. */
@Composable
private fun connectedShapes(
    index: Int,
    count: Int,
): ButtonShapes {
    val shapes =
        when (index) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        }
    return ButtonShapes(shape = shapes.shape, pressedShape = shapes.pressedShape)
}

/** Folds this content away as [state] collapses: its height and opacity follow, read in the layout and draw phases. */
private fun Modifier.collapsingWith(state: TopAppBarState): Modifier =
    clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * (1f - state.collapsedFraction)).toInt()
            layout(placeable.width, height) { placeable.place(0, 0) }
        }.graphicsLayer { alpha = 1f - state.collapsedFraction }

/**
 * "Online now": the contacts TDLib reports online, in the list's order, under a heading with their count; each their
 * avatar (`avatarMedium`, a circle as people's avatars are, with the online dot) over their first name in
 * `labelMedium`, one line, in a keyed `LazyRow`. A tap opens their chat ([onOpen]); screen readers hear one button per
 * person ("Ada, online"). The list leaves the row out while nobody is online.
 */
@Composable
internal fun OnlineNow(
    contacts: ImmutableList<ContactItem>,
    onOpen: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(modifier.padding(bottom = spacing.small)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.large)
                    .padding(top = spacing.large, bottom = spacing.small),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = stringResource(R.string.contacts_online_now),
                modifier = Modifier.weight(1f).semantics { heading() },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            Text(
                text = contacts.size.toString(),
                modifier = Modifier.clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            items(contacts, key = { it.userId }, contentType = { ONLINE_PERSON }) { contact ->
                OnlinePerson(contact, onClick = { onOpen(contact.userId) })
            }
        }
    }
}

/** One person of [OnlineNow]. */
@Composable
private fun OnlinePerson(
    contact: ContactItem,
    onClick: () -> Unit,
) {
    val sizes = MaterialTheme.sizes
    val spacing = MaterialTheme.spacing
    val firstName = remember(contact.name) { contact.name.substringBefore(' ') }
    val description = stringResource(R.string.contacts_online_person, contact.name)
    Column(
        modifier =
            Modifier
                .width(sizes.avatarMedium + spacing.large)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
                .padding(vertical = spacing.extraSmall)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick { onClick().let { true } }
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(
            photo = contact.photo,
            name = contact.name,
            id = contact.userId,
            size = sizes.avatarMedium,
            isOnline = true,
        )
        Text(
            text = firstName,
            modifier = Modifier.padding(top = spacing.extraSmall),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.userContent,
        )
    }
}

/**
 * A letter's header: the letter in `headlineLargeCondensed` `primary`, then a line in
 * `outlineVariant` to the end, on the sheet's `surface`, so rows scroll under it.
 */
@Composable
internal fun LetterHeader(
    letter: String,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = spacing.large)
                .padding(top = spacing.medium, bottom = spacing.extraSmall)
                .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = letter,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.experimentalTypography.headlineLargeCondensed,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f).padding(start = spacing.medium),
            thickness = DividerDefaults.Thickness,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

internal const val ONLINE_ROW = "online"

/** From this font scale on, labels and texts take the room of icons and of buttons beside them. */
internal const val LARGE_FONT_SCALE = 1.5f
private const val ONLINE_PERSON = "onlinePerson"

@PingogramPreviews
@Composable
private fun ContactsExperimentalPreview() {
    PingogramPreview {
        Column {
            OnlineNow(ContactsPreviewData.onlineContacts, onOpen = {})
            LetterHeader("A")
        }
    }
}
