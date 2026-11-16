package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.AvatarTransformKey
import dev.kyralabs.pingogram.core.ui.transition.avatarTransform
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The conversation's top bar title (`docs/DESIGN_SYSTEM.md` §13.5): a centred block, a group's cluster of the people
// who wrote last, the online members in the status.

/**
 * The centred title: the chat's avatar, or in a group up to five people who wrote last overlapping ([MemberCluster]),
 * over the name in `titleMediumEmphasized` (after a secret chat's lock) and the status line in `bodySmall` (a group's
 * online members after its count, in `pingogramColors.online`). One heading and button for screen readers: it opens
 * the profile ([onOpenProfile]) or the info page ([onOpenChatInfo]), the avatar growing into it
 * (`docs/DESIGN_SYSTEM.md` §5). While someone is doing something in the chat, or while TDLib is not connected
 * ([connecting], whose state then replaces the status), the heading is a polite live region, so screen readers
 * announce it ("Grace is typing…", "Connecting…"); the status is not, as "last seen" changes every minute.
 */
@Composable
internal fun ChatTitle(
    header: ChatHeader,
    @StringRes connecting: Int?,
    onOpenProfile: ((userId: Long) -> Unit)?,
    onOpenChatInfo: ((chatId: Long) -> Unit)?,
) {
    val title = header.title.resolve()
    val isAction = header.subtitle is ChatSubtitle.Action
    val userId = header.profileUserId
    val openLabel = stringResource(R.string.chat_open_profile)
    val infoLabel = stringResource(R.string.chat_open_info)
    val openInfo = onOpenChatInfo.takeIf { header.opensChatInfo }
    Column(
        modifier =
            Modifier
                .titleClick(
                    when {
                        userId != null && onOpenProfile != null -> openLabel to { onOpenProfile(userId) }
                        openInfo != null -> infoLabel to { openInfo(header.chatId) }
                        else -> null
                    },
                ).semantics(mergeDescendants = true) {
                    heading()
                    if (isAction || connecting != null) liveRegion = LiveRegionMode.Polite
                },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (header.members.size > 1) {
            MemberCluster(header.members)
        } else {
            Avatar(
                photo = header.photo,
                name = title,
                id = header.chatId,
                size = MaterialTheme.sizes.avatarSmall,
                modifier =
                    Modifier.avatarTransform(
                        avatarTransformKey(userId.takeIf { onOpenProfile != null }, openInfo, header),
                    ),
                placeholderIcon = header.placeholderIcon,
                shape = header.avatarShape,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (header.secret != null) SecretChatLock()
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMediumEmphasized.userContent,
                )
            }
            ChatStatus(header, connecting)
        }
    }
}

/** The title's click, its label for screen readers and what it does; none without one. */
private fun Modifier.titleClick(click: Pair<String, () -> Unit>?): Modifier =
    if (click == null) this else clickable(onClickLabel = click.first, role = Role.Button, onClick = click.second)

/**
 * Up to five people of a group overlapping by a third, each `sizes.avatarExtraSmall` in a ring of the bar's
 * `surfaceContainer`, the newest first at the start; decorative, as the title names the group.
 */
@Composable
private fun MemberCluster(members: List<HeaderMember>) {
    val sizes = MaterialTheme.sizes
    val ring = sizes.avatarRing
    val container = MaterialTheme.colorScheme.surfaceContainer
    Row(horizontalArrangement = Arrangement.spacedBy(-(sizes.avatarExtraSmall / CLUSTER_OVERLAP))) {
        members.forEach { member ->
            Box(
                modifier = Modifier.background(container, CircleShape).padding(ring),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(
                    photo = member.photo,
                    name = member.name.resolve(),
                    id = member.userId,
                    size = sizes.avatarExtraSmall,
                )
            }
        }
    }
}

/** How much of an avatar the next one in the cluster covers: a third. */
private const val CLUSTER_OVERLAP = 3

/**
 * The status line: what TDLib is doing while not connected, what someone is doing (with [TypingDots]), else the status,
 * a group's online members after it ("1,248 members · 36 online").
 */
@Composable
private fun ChatStatus(
    header: ChatHeader,
    @StringRes connecting: Int?,
) {
    val line = StatusLine(connecting, header.subtitle, header.onlineCount)
    val colors = MaterialTheme.colorScheme
    // The connection's state, an action and the status crossfade in place; a status that changes its words does not.
    StateCrossfade(
        targetState = line,
        contentAlignment = Alignment.Center,
        contentKey = { it.kind },
        label = "ChatStatus",
    ) { shown ->
        val subtitle = shown.subtitle
        when {
            shown.connecting != null -> StatusText(stringResource(shown.connecting), colors.onSurfaceVariant)
            subtitle is ChatSubtitle.Action -> ActionStatus(subtitle.text.resolve())
            subtitle is ChatSubtitle.Status -> GroupStatus(subtitle.text.resolve(), shown.onlineCount)
            else -> Unit
        }
    }
}

/** What the status line says: the connection's state while not connected, else the chat's [subtitle]. */
@Immutable
private data class StatusLine(
    @param:StringRes val connecting: Int?,
    val subtitle: ChatSubtitle?,
    val onlineCount: Int,
) {
    /** Which of the three it shows, the content the crossfade tells apart. */
    val kind: Int
        get() =
            when {
                connecting != null -> 0
                subtitle is ChatSubtitle.Action -> 1
                subtitle is ChatSubtitle.Status -> 2
                else -> 3
            }
}

/** What someone is doing, in `primary` after the typing dots. */
@Composable
private fun ActionStatus(text: String) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypingDots(color = primary, style = MaterialTheme.typography.bodySmall)
        StatusText(text, primary)
    }
}

/** The status, and while TDLib counted any, "· 36 online" in `pingogramColors.online` and the emphasized weight. */
@Composable
private fun GroupStatus(
    status: String,
    onlineCount: Int,
) {
    val resources = LocalResources.current
    val online = MaterialTheme.pingogramColors.online
    val weight = MaterialTheme.typography.bodySmallEmphasized.fontWeight
    val text =
        remember(status, onlineCount, online, weight, resources) {
            buildAnnotatedString {
                append(status)
                if (onlineCount > 0) {
                    append(resources.getString(R.string.chat_subtitle_separator))
                    withStyle(SpanStyle(color = online, fontWeight = weight)) {
                        append(resources.getQuantityString(R.plurals.chat_subtitle_online, onlineCount, onlineCount))
                    }
                }
            }
        }
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall.userContent,
    )
}

@Composable
private fun StatusText(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall.userContent,
    )
}

/**
 * The top bar's height, also the search field's, which takes the bar's place: the avatar over the two lines of the
 * title, with room above and below, growing with the font scale instead of clipping them.
 */
@Composable
internal fun titleHeight(): Dp {
    val typography = MaterialTheme.typography
    val spacing = MaterialTheme.spacing
    val lines =
        with(LocalDensity.current) {
            typography.titleMediumEmphasized.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp()
        }
    return max(
        TopAppBarDefaults.TopAppBarExpandedHeight,
        MaterialTheme.sizes.avatarSmall + lines + spacing.extraSmall + spacing.large,
    )
}

/**
 * The lock before a secret chat's name, in `primary` like the chat list's; screen readers hear "Secret chat" in the
 * title's heading.
 */
@Composable
private fun SecretChatLock() {
    Icon(
        painter = painterResource(UiR.drawable.ic_lock),
        contentDescription = stringResource(R.string.secret_chat),
        modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** The key of the title's avatar transform: into [profileUserId]'s profile, else the info page [openInfo] opens. */
private fun avatarTransformKey(
    profileUserId: Long?,
    openInfo: ((chatId: Long) -> Unit)?,
    header: ChatHeader,
): AvatarTransformKey? =
    when {
        profileUserId != null -> AvatarTransformKey.profile(profileUserId)
        openInfo != null -> AvatarTransformKey.chatInfo(header.chatId)
        else -> null
    }
