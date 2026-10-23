package dev.kyralabs.pingogram.feature.profile.invite

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileLabel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The join destination (`ChatInviteRoute` of `:core:navigation`): the join sheet ([ChatInviteSheet]) for its
 * [ChatInviteViewModel], over the screen the link was opened from. Once the user is in the chat (joined, or already a
 * member), the sheet slides away and [onOpenChat] opens it; [onClose] leaves the destination once the sheet closed
 * (dismissed by a drag, the scrim, back or "Cancel", after its exit animation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatInviteSheetRoute(
    onOpenChat: (chatId: Long) -> Unit,
    onClose: () -> Unit,
    viewModel: ChatInviteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatInviteEffect.OpenChat -> {
                    sheetState.hide()
                    currentOnOpenChat(effect.chatId)
                }
            }
        }
    }
    ChatInviteSheet(
        state = state,
        onEvent = viewModel::onEvent,
        onCancel = { scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnClose() } },
        onDismiss = { currentOnClose() },
        sheetState = sheetState,
    )
}

/**
 * The join sheet (`docs/DESIGN_SYSTEM.md` §6 "Join sheet"): a `ModalBottomSheet` with [ChatInviteContent], expanded
 * at once and scrolling where it does not fit. [onCancel] closes it from inside, [onDismiss] once a drag, the scrim or
 * back dismissed it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatInviteSheet(
    state: ChatInviteUiState,
    onEvent: (ChatInviteEvent) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ChatInviteContent(
            state = state,
            onEvent = onEvent,
            onCancel = onCancel,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

/**
 * What the join sheet shows for [state], each step crossfading on the effects springs while the sheet resizes on the
 * default spatial spring: the delayed loading indicator; the chat ([InviteDetails]) with "Cancel" and "Join" (or
 * "Request to join"); that the request was sent; or why the link does not open, with "Try again" where that may help.
 */
@Composable
internal fun ChatInviteContent(
    state: ChatInviteUiState,
    onEvent: (ChatInviteEvent) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.large),
        transitionSpec = {
            (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() })
        },
        // A new step crossfades; the same step changing (joining, an error) does not.
        contentKey = { it::class },
        contentAlignment = Alignment.TopCenter,
        label = "inviteStep",
    ) { step ->
        Box(Modifier.fillMaxWidth().widthIn(max = MaterialTheme.sizes.contentMaxWidth), Alignment.TopCenter) {
            when (step) {
                ChatInviteUiState.Loading -> {
                    DelayedLoadingIndicator(Modifier.heightIn(min = MaterialTheme.sizes.illustrationMaxSize))
                }

                is ChatInviteUiState.Content -> {
                    InviteDetails(step, onEvent, onCancel)
                }

                is ChatInviteUiState.RequestSent -> {
                    RequestSent(step.kind, onDone = onCancel)
                }

                is ChatInviteUiState.Failed -> {
                    InviteFailed(step, onEvent, onCancel)
                }
            }
        }
    }
}

/**
 * The chat: its avatar (`avatarLarge`), its name in `titleLargeEmphasized` with a verified badge or a "Scam" / "Fake"
 * label, its kind and members in `bodyMedium`, a few members' avatars, its description, then what joining does, and
 * the actions. A paid chat says that Pingogram does not offer subscriptions and has no "Join".
 */
@Composable
private fun InviteDetails(
    state: ChatInviteUiState.Content,
    onEvent: (ChatInviteEvent) -> Unit,
    onCancel: () -> Unit,
) {
    val invite = state.invite
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Avatar(
            photo = invite.photo,
            name = invite.title,
            id = invite.avatarId,
            size = MaterialTheme.sizes.avatarLarge,
        )
        InviteTitle(invite)
        Text(
            text = inviteStatus(invite),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (invite.members.isNotEmpty()) InviteMembers(invite.members)
        if (invite.description.isNotBlank()) {
            Text(
                text = invite.description,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
        joinNote(state)?.let { note ->
            Text(
                text = stringResource(note),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.joinError?.let { JoinError(it) }
        if (state.isJoining) {
            val joining = stringResource(R.string.invite_joining)
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = joining })
        }
        InviteActions(
            primary = if (invite.needsSubscription) null else joinLabel(invite),
            enabled = state.canJoin,
            onPrimary = { onEvent(ChatInviteEvent.Join) },
            secondary = R.string.invite_cancel,
            onSecondary = onCancel,
        )
    }
}

/** The chat's name, a heading, with its verified badge in `primary` or its warning label. */
@Composable
private fun InviteTitle(invite: InviteCard) {
    val colors = MaterialTheme.colorScheme
    val verified = stringResource(R.string.profile_badge_verified).takeIf { invite.isVerified }
    val warning =
        when {
            invite.isScam -> stringResource(R.string.profile_badge_scam)
            invite.isFake -> stringResource(R.string.profile_badge_fake)
            else -> null
        }
    val resources = LocalResources.current
    Row(
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                heading()
                // The badge and the label are read with the title, the warning last.
                if (invite.isVerified) {
                    contentDescription =
                        spokenList(resources, listOf(invite.title, verified, warning))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = invite.title,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLargeEmphasized.userContent,
        )
        if (invite.isVerified) {
            Icon(
                painter = painterResource(UiR.drawable.ic_verified_filled),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                tint = colors.primary,
            )
        }
        when {
            invite.isScam -> {
                ProfileLabel(
                    stringResource(R.string.profile_badge_scam),
                    colors.errorContainer,
                    colors.onErrorContainer,
                )
            }

            invite.isFake -> {
                ProfileLabel(
                    stringResource(R.string.profile_badge_fake),
                    colors.errorContainer,
                    colors.onErrorContainer,
                )
            }
        }
    }
}

/** A few members as small avatars side by side, read as one: "Members include Ada and Grace". */
@Composable
private fun InviteMembers(members: List<InviteMember>) {
    val names = members.map { it.name.resolve() }
    val description = stringResource(R.string.invite_members_include, spokenList(LocalResources.current, names))
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        members.forEachIndexed { index, member ->
            Avatar(
                photo = member.photo,
                name = names[index],
                id = member.id,
                size = MaterialTheme.sizes.avatarExtraSmall,
            )
        }
    }
}

/** Why joining failed, in `error`, said by screen readers as it appears. */
@Composable
private fun JoinError(error: DomainError) {
    val text = errorMessage(LocalResources.current, error, isDebuggable())
    if (text.isEmpty()) return
    Text(
        text = text,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** "Public group · 1,024 members": the kind of chat and how many are in it. */
@Composable
private fun inviteStatus(invite: InviteCard): String {
    val kind =
        stringResource(
            when (invite.kind) {
                GroupKind.Channel -> {
                    if (invite.isPublic) R.string.chat_info_public_channel else R.string.chat_info_private_channel
                }

                else -> {
                    if (invite.isPublic) R.string.chat_info_public_group else R.string.chat_info_private_group
                }
            },
        )
    val count = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(invite.memberCount)
    val members =
        pluralStringResource(
            if (invite.kind == GroupKind.Channel) R.plurals.chat_info_subscribers else R.plurals.chat_info_members,
            invite.memberCount,
            count,
        )
    return stringResource(R.string.chat_info_status, kind, members)
}

/** "Join group", "Join channel" or "Request to join". */
@StringRes
private fun joinLabel(invite: InviteCard): Int =
    when {
        invite.createsJoinRequest -> R.string.invite_request_to_join
        invite.kind == GroupKind.Channel -> R.string.invite_join_channel
        else -> R.string.invite_join_group
    }

/** What joining does, where it is more than joining: a request, a paid subscription, or what came of the last try. */
@StringRes
private fun joinNote(state: ChatInviteUiState.Content): Int? =
    when {
        state.notice == JoinNotice.NeedsBotApproval -> R.string.invite_needs_bot_approval
        state.notice == JoinNotice.Declined -> R.string.invite_declined
        state.invite.needsSubscription -> R.string.invite_needs_subscription
        !state.invite.createsJoinRequest -> null
        state.invite.kind == GroupKind.Channel -> R.string.invite_request_note_channel
        else -> R.string.invite_request_note_group
    }

/** The lines the chat's name may take. */
private const val TITLE_MAX_LINES = 3
