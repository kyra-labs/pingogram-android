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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
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
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R

// The conversation's top bar in the experimental look (`docs/DESIGN_SYSTEM.md` §13.5): a centred title block, a group's
// cluster of the people who wrote last, the online members in the status, and a channel's mute toggle and menu.

/**
 * The experimental look's centred title: the chat's avatar, or in a group up to five people who wrote last overlapping
 * ([MemberCluster]), over the name in `titleMediumEmphasized` and the status line in `bodySmall` (a group's online
 * members after its count, in `pingogramColors.online`). One heading and button for screen readers, as [ChatTitle]:
 * it opens the profile or the info page.
 */
@Composable
internal fun ExperimentalChatTitle(
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
                placeholderIcon = header.placeholderIcon,
                shape = header.avatarShape,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMediumEmphasized.userContent,
            )
            ExperimentalStatus(header, connecting)
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
private fun ExperimentalStatus(
    header: ChatHeader,
    @StringRes connecting: Int?,
) {
    val subtitle = header.subtitle
    val colors = MaterialTheme.colorScheme
    when {
        connecting != null -> StatusText(stringResource(connecting), colors.onSurfaceVariant)
        subtitle is ChatSubtitle.Action -> ActionStatus(subtitle.text.resolve())
        subtitle is ChatSubtitle.Status -> GroupStatus(subtitle.text.resolve(), header.onlineCount)
        else -> Unit
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

/** The experimental top bar's height: the avatar over the two lines, with room above and below. */
@Composable
internal fun experimentalTitleHeight(): Dp {
    val typography = MaterialTheme.typography
    val spacing = MaterialTheme.spacing
    val lines =
        with(LocalDensity.current) {
            typography.titleMediumEmphasized.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp()
        }
    return max(titleHeight(), MaterialTheme.sizes.avatarSmall + lines + spacing.extraSmall + spacing.large)
}

/**
 * The experimental look's top bar (`docs/DESIGN_SYSTEM.md` §13.5): Material's center-aligned top bar with back, the
 * centred title block ([ExperimentalChatTitle]) and the actions: [ExperimentalGroupActions] in groups and channels,
 * [ChatActions] elsewhere (the call button and the secret chat's timer as in the standard look).
 */
@Suppress("LongParameterList") // ChatTopBar's own parameters, passed on.
@Composable
internal fun ExperimentalChatTopBar(
    header: ChatHeader,
    onBack: (() -> Unit)?,
    modifier: Modifier,
    onOpenProfile: ((userId: Long) -> Unit)?,
    onSecretChatEvent: (SecretChatEvent) -> Unit,
    onOpenChatInfo: ((chatId: Long) -> Unit)?,
    onSearch: (() -> Unit)?,
    topicAction: (@Composable () -> Unit)?,
    connection: ConnectionStatus,
    onSetMuted: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    CenterAlignedTopAppBar(
        title = { ExperimentalChatTitle(header, connection.labelRes(), onOpenProfile, onOpenChatInfo) },
        modifier = modifier,
        navigationIcon = { ChatNavigationIcon(selectedCount = null, onClearSelection = {}, onBack = onBack) },
        actions = {
            val openInfo = onOpenChatInfo.takeIf { header.opensChatInfo }
            if (header.opensChatInfo && topicAction == null) {
                ExperimentalGroupActions(header, onSearch, openInfo?.let { { it(header.chatId) } }, onSetMuted)
            } else {
                ChatActions(header, onSearch, onSecretChatEvent, topicAction)
            }
        },
        expandedHeight = experimentalTitleHeight(),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colors.surfaceContainer,
                scrolledContainerColor = colors.surfaceContainer,
            ),
    )
}
