package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.ui.component.LocalVoiceChats
import dev.kyralabs.pingogram.core.ui.component.VoiceChatSummary
import dev.kyralabs.pingogram.core.ui.component.VoiceChats
import dev.kyralabs.pingogram.core.ui.text.mentionTarget
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ActionPill
import dev.kyralabs.pingogram.feature.profile.ui.ProfileGroup
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRow
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import kotlinx.coroutines.flow.flowOf
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The page's actions as pills under the header, as the profile's (`docs/DESIGN_SYSTEM.md` §6 "Group and channel
 * info"): the mute toggle with a `ToggleOn` or `ToggleOff` haptic, chat [chatId]'s voice chat ([VoiceChatPill]),
 * then "Leave", all
 * `FilledTonalButton`s with their icon over their label, sharing the row's width. Nothing for a pill that does not
 * apply.
 */
@Composable
internal fun ChatInfoActionsRow(
    chatId: Long,
    actions: ChatInfoActions,
    onEvent: (ChatInfoEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val isMuted = actions.isMuted
    val voiceChats = LocalVoiceChats.current
    val voiceChat by remember(voiceChats, chatId) { voiceChats?.voiceChat(chatId) ?: flowOf(null) }
        .collectAsStateWithLifecycle(null)
    val voiceChatPill = voiceChats != null && (voiceChat != null || actions.canStartVoiceChat)
    if (isMuted == null && !actions.canLeave && !voiceChatPill) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (isMuted != null) {
            ActionPill(
                icon = if (isMuted) UiR.drawable.ic_notifications else UiR.drawable.ic_notifications_off,
                label = stringResource(if (isMuted) R.string.profile_action_unmute else R.string.profile_action_mute),
                onClick = {
                    haptics.performHapticFeedback(
                        if (isMuted) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                    )
                    onEvent(ChatInfoEvent.ToggleMute)
                },
                modifier = Modifier.weight(1f),
            )
        }
        if (voiceChats != null && voiceChatPill) {
            VoiceChatPill(chatId, voiceChat, voiceChats, Modifier.weight(1f))
        }
        if (actions.canLeave) {
            ActionPill(
                icon = R.drawable.ic_logout,
                label = stringResource(R.string.chat_info_action_leave),
                onClick = { onEvent(ChatInfoEvent.Leave) },
                enabled = !actions.isBusy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The voice chat's pill (`docs/adr/0023-group-voice-chats-and-live-streams.md`): "Voice chat" (or "Live stream") joins
 * the one under way; "Start voice chat", for those who may while there is none, starts one.
 */
@Composable
private fun VoiceChatPill(
    chatId: Long,
    current: VoiceChatSummary?,
    voiceChats: VoiceChats,
    modifier: Modifier = Modifier,
) {
    ActionPill(
        icon = if (current?.isLiveStream == true) UiR.drawable.ic_live_tv else UiR.drawable.ic_graphic_eq,
        label =
            stringResource(
                when {
                    current == null -> R.string.chat_info_action_start_voice_chat
                    current.isLiveStream -> R.string.chat_info_action_live_stream
                    else -> R.string.chat_info_action_voice_chat
                },
            ),
        onClick = { if (current == null) voiceChats.start(chatId) else voiceChats.join(chatId) },
        modifier = modifier,
    )
}

/**
 * The information rows, segmented list items without icons, each value over its label as the profile's: the
 * description with its links and mentions (selectable; a mention opens who it names; "Copy" for screen readers), then
 * the link ("t.me/rustberlin", or the invite link for administrators of a private group), which a tap copies and the
 * trailing button shares, and "QR code", which shows it as one.
 */
@Composable
internal fun ChatInfoGroup(
    description: FormattedText?,
    link: ChatLink?,
    onEvent: (ChatInfoEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = mutableListOf<ProfileRow>()
    description?.let { text -> rows += ProfileRow("description") { DescriptionRow(text, it, onEvent) } }
    link?.let { chatLink ->
        rows += ProfileRow("link") { LinkRow(chatLink, it, onEvent) }
        rows +=
            ProfileRow("qr") { shapes ->
                ChatInfoActionRow(
                    stringResource(R.string.chat_info_qr_code),
                    shapes,
                    { onEvent(ChatInfoEvent.ShowQrCode) },
                )
            }
    }
    ProfileGroup(rows, modifier, title = stringResource(R.string.profile_section_info))
}

@Composable
private fun DescriptionRow(
    description: FormattedText,
    shapes: ListItemShapes,
    onEvent: (ChatInfoEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val styles = rememberFormattedTextStyles()
    val onMention =
        remember {
            LinkInteractionListener { link: LinkAnnotation ->
                mentionTarget(link)?.let { currentOnEvent(ChatInfoEvent.OpenMention(it)) }
            }
        }
    val annotated =
        remember(description, styles, onMention) { description.toAnnotatedString(styles, onMentionClick = onMention) }
    val copy = stringResource(R.string.profile_copy)
    SegmentedListItem(
        shapes = shapes,
        modifier =
            Modifier.semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(copy) {
                            currentOnEvent(ChatInfoEvent.CopyDescription)
                            true
                        },
                    )
            },
        colors = profileRowColors(),
        supportingContent = { Text(stringResource(R.string.chat_info_description)) },
    ) {
        SelectionContainer {
            Text(annotated, style = MaterialTheme.typography.bodyLarge.userContent)
        }
    }
}

@Composable
private fun LinkRow(
    link: ChatLink,
    shapes: ListItemShapes,
    onEvent: (ChatInfoEvent) -> Unit,
) {
    val copy = stringResource(R.string.profile_copy)
    SegmentedListItem(
        onClick = { onEvent(ChatInfoEvent.CopyLink) },
        // Names what a tap does; the list item's click supplies the action.
        modifier = Modifier.semantics { onClick(label = copy, action = null) },
        shapes = shapes,
        colors = profileRowColors(),
        supportingContent = {
            Text(stringResource(if (link.isPublic) R.string.chat_info_link else R.string.chat_info_invite_link))
        },
        trailingContent = {
            IconButton(onClick = { onEvent(ChatInfoEvent.ShareLink) }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_share),
                    contentDescription = stringResource(R.string.chat_info_share_link),
                )
            }
        },
    ) {
        Text(link.text, style = MaterialTheme.typography.bodyLarge.numericContent)
    }
}

/**
 * The first members under "Members" ("Subscribers" in a channel), each opening their profile, then "All members" with
 * their count, which opens the members page; where the group hides its members, a row that says so instead.
 */
@Composable
internal fun ChatMembersGroup(
    members: MembersPreview,
    kind: GroupKind,
    onEvent: (ChatInfoEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val isChannel = kind == GroupKind.Channel
    val rows = mutableListOf<ProfileRow>()
    if (members.isHidden) {
        rows +=
            ProfileRow("hidden") { shapes ->
                SegmentedListItem(shapes = shapes, colors = profileRowColors()) {
                    Text(stringResource(R.string.chat_info_members_hidden))
                }
            }
    } else {
        members.items.forEach { item ->
            rows +=
                ProfileRow(item.key) { shapes ->
                    MemberRow(item, shapes, onClick = { onEvent(ChatInfoEvent.OpenMember(item.sender)) })
                }
        }
        rows +=
            ProfileRow("all") { shapes ->
                ChatInfoActionRow(
                    text =
                        pluralStringResource(
                            if (isChannel) R.plurals.chat_info_all_subscribers else R.plurals.chat_info_all_members,
                            members.totalCount,
                            numbers.format(members.totalCount),
                        ),
                    shapes = shapes,
                    onClick = { onEvent(ChatInfoEvent.OpenMembers(MemberFilter.Recent)) },
                )
            }
    }
    val title =
        stringResource(if (isChannel) R.string.chat_info_section_subscribers else R.string.chat_info_section_members)
    ProfileGroup(rows, modifier, title = title)
}

/**
 * What administrators manage, each row where their rights allow it (`docs/DESIGN_SYSTEM.md` §6): "Name and
 * description", "Permissions" (groups), "Administrators", "Invite links", "Restricted users", "Removed users" and, for
 * supergroups and channels, "Boosts" (read only), the counts as supporting text. Each opens its page.
 */
@Composable
internal fun ChatAdministrationGroup(
    administration: ChatAdministration,
    onEvent: (ChatInfoEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])

    fun page(
        key: String,
        @StringRes text: Int,
        page: ChatInfoPage,
    ) = ProfileRow(key) { shapes ->
        ChatInfoActionRow(stringResource(text), shapes, { onEvent(ChatInfoEvent.Open(page)) })
    }

    fun members(
        key: String,
        @StringRes text: Int,
        filter: MemberFilter,
        count: Int,
    ) = ProfileRow(key) { shapes ->
        ChatInfoActionRow(
            text = stringResource(text),
            shapes = shapes,
            onClick = { onEvent(ChatInfoEvent.OpenMembers(filter)) },
            detail = numbers.format(count),
        )
    }
    val rows =
        listOfNotNull(
            page("edit", R.string.chat_info_edit, ChatInfoPage.EditInfo).takeIf { administration.canEditInfo },
            page("permissions", R.string.chat_info_permissions, ChatInfoPage.Permissions)
                .takeIf { administration.canEditPermissions },
            administration.administratorCount?.let {
                members("admins", R.string.chat_info_administrators, MemberFilter.Administrators, it)
            },
            page("links", R.string.chat_info_invite_links, ChatInfoPage.InviteLinks)
                .takeIf { administration.canManageInviteLinks },
            administration.restrictedCount?.let {
                members("restricted", R.string.chat_info_restricted, MemberFilter.Restricted, it)
            },
            administration.bannedCount?.let { members("banned", R.string.chat_info_removed, MemberFilter.Banned, it) },
            page("boosts", R.string.chat_info_boosts, ChatInfoPage.Boosts).takeIf { administration.canViewBoosts },
        )
    ProfileGroup(rows, modifier, title = stringResource(R.string.chat_info_section_administration))
}

/**
 * The rest, segmented rows without icons: "Export chat" (`docs/adr/0021-chat-export.md`, where the chat lets its
 * content be saved), "Clear history" (groups) and, for the owner, "Delete group" or "Delete channel" in `error`; the
 * last two ask first.
 */
@Composable
internal fun ChatInfoMoreGroup(
    actions: ChatInfoActions,
    kind: GroupKind,
    onEvent: (ChatInfoEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = mutableListOf<ProfileRow>()
    if (actions.canExport) {
        rows +=
            ProfileRow("export") { shapes ->
                ChatInfoActionRow(
                    stringResource(R.string.profile_export_chat),
                    shapes,
                    { onEvent(ChatInfoEvent.Open(ChatInfoPage.Export)) },
                )
            }
    }
    if (actions.canClearHistory) {
        rows +=
            ProfileRow("clear") { shapes ->
                ChatInfoActionRow(
                    stringResource(R.string.chat_info_clear_history),
                    shapes,
                    { onEvent(ChatInfoEvent.ClearHistory) },
                    enabled = !actions.isBusy,
                )
            }
    }
    if (actions.canDelete) {
        rows +=
            ProfileRow("delete") { shapes ->
                ChatInfoActionRow(
                    text =
                        stringResource(
                            if (kind == GroupKind.Channel) {
                                R.string.chat_info_delete_channel
                            } else {
                                R.string.chat_info_delete_group
                            },
                        ),
                    shapes = shapes,
                    onClick = { onEvent(ChatInfoEvent.Delete) },
                    enabled = !actions.isBusy,
                    contentColor = MaterialTheme.colorScheme.error,
                )
            }
    }
    ProfileGroup(rows, modifier, title = stringResource(R.string.profile_section_more))
}

/** A row that does what [text] says, with [detail] (a count) as its supporting text. */
@Suppress("LongParameterList") // What the row shows and does.
@Composable
internal fun ChatInfoActionRow(
    text: String,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    detail: String? = null,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        enabled = enabled,
        colors = profileRowColors(contentColor),
        supportingContent = detail?.let { { Text(it) } },
    ) {
        Text(text)
    }
}
