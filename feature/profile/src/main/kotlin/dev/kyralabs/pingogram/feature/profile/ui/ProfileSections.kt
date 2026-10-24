package dev.kyralabs.pingogram.feature.profile.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.component.CallButtonMenu
import dev.kyralabs.pingogram.core.ui.component.LocalCallStarter
import dev.kyralabs.pingogram.core.ui.text.formatPhoneNumber
import dev.kyralabs.pingogram.core.ui.text.mentionTarget
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.phoneNumberToShow
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The profile's actions as pills under the header, as Google Contacts' expressive contact page has them
 * (`docs/DESIGN_SYSTEM.md` §6 "Profile actions"): "Message" (Saved Messages on one's own profile) as a filled `Button`,
 * the primary action, then "Call" (where the user can be called, through [LocalCallStarter]; a menu of a voice and a
 * video call where the user can get a video call, [CallButtonMenu]), the mute toggle and
 * "Secret chat" as `FilledTonalButton`s, each its icon over its label,
 * sharing the row's width, all with `ButtonDefaults.shapes()`' press morph. The mute toggle gives a `ToggleOn` or
 * `ToggleOff` haptic. Pills that do not apply are left out; the row is empty for a deleted account.
 */
@Composable
internal fun ProfileActionsRow(
    actions: ProfileActions,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val isMuted = actions.isMuted
    val calls = LocalCallStarter.current
    val callUserId = actions.callUserId.takeIf { calls != null }
    val hasChatPills = actions.canMessage || isMuted != null || actions.canStartSecretChat
    if (!hasChatPills && callUserId == null) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (actions.canMessage) {
            ActionPill(
                icon = UiR.drawable.ic_chat_filled,
                label =
                    stringResource(
                        if (actions.isSelf) R.string.profile_action_saved_messages else R.string.profile_action_message,
                    ),
                onClick = { onEvent(ProfileEvent.SendMessage) },
                primary = true,
                modifier = Modifier.weight(1f),
            )
        }
        if (calls != null && callUserId != null) {
            // A voice call at once, or the voice and video menu where the user can get a video call.
            CallButtonMenu(userId = callUserId, calls = calls, modifier = Modifier.weight(1f)) { onClick, menu ->
                ActionPill(
                    icon = UiR.drawable.ic_call_filled,
                    label = stringResource(R.string.profile_action_call),
                    onClick = onClick,
                    modifier = menu.fillMaxWidth(),
                )
            }
        }
        if (isMuted != null) {
            ActionPill(
                icon = if (isMuted) UiR.drawable.ic_notifications else UiR.drawable.ic_notifications_off,
                label = stringResource(if (isMuted) R.string.profile_action_unmute else R.string.profile_action_mute),
                onClick = {
                    haptics.performHapticFeedback(
                        if (isMuted) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                    onEvent(ProfileEvent.ToggleMute)
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (actions.canStartSecretChat) {
            ActionPill(
                icon = UiR.drawable.ic_lock,
                label = stringResource(R.string.profile_action_secret_chat),
                onClick = { onEvent(ProfileEvent.StartSecretChat) },
                enabled = !actions.isStartingSecretChat,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One action: [icon] over [label], filled for the [primary] action, tonal otherwise. */
@Composable
internal fun ActionPill(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val spacing = MaterialTheme.spacing
    val padding = PaddingValues(horizontal = spacing.small, vertical = spacing.medium)
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painter = painterResource(icon), contentDescription = null)
            Text(
                text = label,
                textAlign = TextAlign.Center,
                style = with(MaterialTheme.typography) { if (primary) labelLargeEmphasized else labelLarge },
            )
        }
    }
    if (primary) {
        Button(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
            contentPadding = padding,
        ) { content() }
    } else {
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
            contentPadding = padding,
        ) { content() }
    }
}

/**
 * The information rows (`docs/DESIGN_SYSTEM.md` §6 "Profile info"), segmented list items without icons, each value over
 * its label in the list item's supporting text, as Google Contacts lists a contact's details:
 * - **Bio** (or a bot's "About"): its text with links and mentions, selectable; tapping a link opens it, a mention the
 *   profile or chat it names ([ProfileEvent.OpenMention]); screen readers get "Copy" as a custom action.
 * - **Usernames**: `@name`; tapping copies it, the trailing button shares its `t.me` link.
 * - **Phone**: formatted for its country, left to right; tapping opens the dialer, a long press or the trailing button
 *   copies it.
 * - **Birthday**, formatted by the view model.
 * - **Channel**: the channel the user shows on their profile, its title over "Channel"; tapping opens it.
 *
 * User content takes its own direction (`userContent`), the phone number left to right (`numericContent`).
 */
@Composable
internal fun ProfileInfoGroup(
    info: ProfileInfo,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = mutableListOf<ProfileRow>()
    info.bio?.let { bio -> rows += ProfileRow("bio") { BioRow(bio, info.isBot, it, onEvent) } }
    info.usernames.forEach { username ->
        rows += ProfileRow("username:$username") { UsernameRow(username, it, onEvent) }
    }
    info.phoneNumber?.let { phone -> rows += ProfileRow("phone") { PhoneRow(phone, it, onEvent) } }
    info.birthday?.let { birthday ->
        rows +=
            ProfileRow("birthday") { shapes ->
                SegmentedListItem(
                    shapes = shapes,
                    colors = profileRowColors(),
                    supportingContent = { Text(stringResource(R.string.profile_birthday)) },
                ) {
                    Text(birthday, style = MaterialTheme.typography.bodyLarge.userContent)
                }
            }
    }
    info.personalChannel?.let { channel ->
        rows += ProfileRow("personalChannel") { shapes -> PersonalChannelRow(channel, shapes, onEvent) }
    }
    ProfileGroup(rows, modifier, title = stringResource(R.string.profile_section_info))
}

/** The personal channel: its title (user content) over "Channel"; a tap opens its chat. */
@Composable
private fun PersonalChannelRow(
    channel: PersonalChannel,
    shapes: ListItemShapes,
    onEvent: (ProfileEvent) -> Unit,
) {
    SegmentedListItem(
        onClick = { onEvent(ProfileEvent.OpenPersonalChannel(channel.chatId)) },
        shapes = shapes,
        colors = profileRowColors(),
        supportingContent = { Text(stringResource(R.string.profile_personal_channel)) },
    ) {
        Text(channel.title, style = MaterialTheme.typography.bodyLarge.userContent)
    }
}

@Composable
private fun BioRow(
    bio: FormattedText,
    isBot: Boolean,
    shapes: ListItemShapes,
    onEvent: (ProfileEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val styles = rememberFormattedTextStyles()
    val onMention = remember { LinkInteractionListener { link -> mentionOf(link)?.let(currentOnEvent) } }
    val annotated = remember(bio, styles, onMention) { bio.toAnnotatedString(styles, onMentionClick = onMention) }
    val copy = stringResource(R.string.profile_copy)
    SegmentedListItem(
        shapes = shapes,
        modifier =
            Modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(copy) {
                            currentOnEvent(ProfileEvent.CopyBio)
                            true
                        },
                    )
            },
        colors = profileRowColors(),
        supportingContent = { Text(stringResource(if (isBot) R.string.profile_bot_about else R.string.profile_bio)) },
    ) {
        SelectionContainer {
            Text(annotated, style = MaterialTheme.typography.bodyLarge.userContent)
        }
    }
}

/** The event of a tapped mention; null for other links, which open by themselves. */
private fun mentionOf(link: LinkAnnotation): ProfileEvent? = mentionTarget(link)?.let(ProfileEvent::OpenMention)

@Composable
private fun UsernameRow(
    username: String,
    shapes: ListItemShapes,
    onEvent: (ProfileEvent) -> Unit,
) {
    val copy = stringResource(R.string.profile_copy)
    SegmentedListItem(
        onClick = { onEvent(ProfileEvent.CopyUsername(username)) },
        // Names what a tap does; the list item's click supplies the action.
        modifier = Modifier.semantics { onClick(label = copy, action = null) },
        shapes = shapes,
        colors = profileRowColors(),
        supportingContent = { Text(stringResource(R.string.profile_username)) },
        trailingContent = {
            IconButton(
                onClick = { onEvent(ProfileEvent.ShareUsername(username)) },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_share),
                    contentDescription = stringResource(R.string.profile_share_username),
                )
            }
        },
    ) {
        Text(
            stringResource(R.string.profile_username_value, username),
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

@Composable
private fun PhoneRow(
    phoneNumber: String,
    shapes: ListItemShapes,
    onEvent: (ProfileEvent) -> Unit,
) {
    val country = LocalConfiguration.current.locales[0].country
    val formatted = phoneNumberToShow(remember(phoneNumber, country) { formatPhoneNumber(phoneNumber, country) })
    val call = stringResource(R.string.profile_call)
    SegmentedListItem(
        onClick = { onEvent(ProfileEvent.CallPhone) },
        modifier = Modifier.semantics { onClick(label = call, action = null) },
        shapes = shapes,
        colors = profileRowColors(),
        supportingContent = { Text(stringResource(R.string.profile_phone)) },
        onLongClick = { onEvent(ProfileEvent.CopyPhone) },
        onLongClickLabel = stringResource(R.string.profile_copy),
        trailingContent = {
            IconButton(onClick = { onEvent(ProfileEvent.CopyPhone) }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_content_copy),
                    contentDescription = stringResource(R.string.profile_copy_phone),
                )
            }
        },
    ) {
        Text(formatted, style = MaterialTheme.typography.bodyLarge.numericContent)
    }
}

/**
 * What else can be done with the account, segmented rows without icons: "Add to contacts" or "Edit contact" (only with
 * [onEditContact], the app's contact editor), "Export chat" (only with [onExportChat], the app's export page),
 * "Block user" in `error` (after a confirmation) or "Unblock user", and "Report" in `error`, which opens the report
 * sheet. Rows that do not apply are left out.
 */
@Composable
internal fun ProfileMoreGroup(
    actions: ProfileActions,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
    onEditContact: (() -> Unit)? = null,
    onExportChat: (() -> Unit)? = null,
) {
    val error = MaterialTheme.colorScheme.error
    val rows = mutableListOf<ProfileRow>()
    val contact = actions.contact
    if (contact != null && onEditContact != null) {
        val label = if (contact == ContactAction.Edit) R.string.profile_edit_contact else R.string.profile_add_contact
        rows += ProfileRow("contact") { ActionRow(stringResource(label), it, onClick = onEditContact) }
    }
    if (onExportChat != null) {
        rows +=
            ProfileRow("export") { ActionRow(stringResource(R.string.profile_export_chat), it, onClick = onExportChat) }
    }
    actions.isBlocked?.let { isBlocked ->
        rows +=
            ProfileRow("block") { shapes ->
                ActionRow(
                    text = stringResource(if (isBlocked) R.string.profile_unblock else R.string.profile_block),
                    shapes = shapes,
                    onClick = { onEvent(if (isBlocked) ProfileEvent.Unblock else ProfileEvent.Block) },
                    enabled = !actions.isChangingBlock,
                    contentColor = if (isBlocked) Color.Unspecified else error,
                )
            }
    }
    if (actions.canReport) {
        rows +=
            ProfileRow("report") { shapes ->
                ActionRow(
                    text = stringResource(R.string.profile_report),
                    shapes = shapes,
                    onClick = { onEvent(ProfileEvent.Report) },
                    contentColor = error,
                )
            }
    }
    ProfileGroup(rows, modifier, title = stringResource(R.string.profile_section_more))
}

@Composable
private fun ActionRow(
    text: String,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        enabled = enabled,
        colors = profileRowColors(contentColor),
    ) {
        Text(text)
    }
}
