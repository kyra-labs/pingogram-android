package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.LocalCallStarter
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.AvatarTransformKey
import dev.kyralabs.pingogram.core.ui.transition.avatarTransform
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.secret.selfDestructShortText
import dev.kyralabs.pingogram.feature.chat.ui.secret.selfDestructText
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The conversation's small [TopAppBar] (`docs/DESIGN_SYSTEM.md` §6): back, then the chat's avatar and a two-line
 * title, its name in `titleMediumEmphasized` over the subtitle in `bodySmall`: `onSurfaceVariant`, or `primary` with
 * pulsing [TypingDots] while someone is typing or doing something else in the chat. Every chat has a search button
 * ([onSearch], `REQUIREMENTS.md` §5.9), and a private chat with a person a call button after it
 * ([ChatHeader.callUserId], through [LocalCallStarter]): Material allows two actions at most. In a private or secret
 * chat the
 * title opens the other user's profile through [onOpenProfile] (`REQUIREMENTS.md` FR-P2), its state layer over the
 * title; in a group or channel it opens the chat's info page through [onOpenChatInfo] (FR-P3). A secret chat
 * (`REQUIREMENTS.md` FR-E1, [ChatHeader.secret]) has a lock before its name, and its self-destruct timer after the
 * search button, with the timer on a badge, through [onSecretChatEvent] ([SecretChatActions]); its encryption key opens
 * from the timer's sheet, as two actions are all Material allows (without search, the key's button follows). It sits on
 * the screen's `surfaceContainer`, above the sheet the messages scroll in. A forum's topic list and a topic's
 * conversation add their topic action ([topicAction]: "New topic", "Manage topic") after search. Without [onBack]
 * there is no back button: in the detail pane beside the chat list, which has nothing to go back to.
 *
 * It grows with the font scale instead of clipping its two lines, which the default height fits only up to about
 * 150%.
 *
 * While TDLib is not connected ([connection]), "Waiting for network", "Connecting…" or "Updating…" takes the status's
 * place (and that of what someone is doing, which cannot arrive meanwhile), as Telegram's apps do, in
 * `onSurfaceVariant`; screen readers hear it change.
 *
 * In selection mode ([selectedCount] not null, §6 "Selection mode") the same bar shows "3 selected" in `titleLarge`
 * with a close button that leaves it ([onClearSelection]) in place of back, the titles cross-fading on the motion
 * scheme's effects springs; screen readers hear the count change.
 */
@Composable
internal fun ChatTopBar(
    header: ChatHeader?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selectedCount: Int? = null,
    onClearSelection: () -> Unit = {},
    onOpenProfile: ((userId: Long) -> Unit)? = null,
    onSecretChatEvent: (SecretChatEvent) -> Unit = {},
    onOpenChatInfo: ((chatId: Long) -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    topicAction: (@Composable () -> Unit)? = null,
    connection: ConnectionStatus = ConnectionStatus.Ready,
) {
    val colors = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    TopAppBar(
        title = {
            AnimatedContent(
                targetState = selectedCount,
                transitionSpec = {
                    fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
                },
                contentKey = { it != null },
                label = "chatTitle",
            ) { count ->
                when {
                    count != null -> SelectionTitle(count)
                    header != null -> ChatTitle(header, connection, onOpenProfile, onOpenChatInfo)
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            when {
                selectedCount != null -> {
                    IconButton(onClick = onClearSelection, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_close),
                            contentDescription = stringResource(R.string.selection_clear),
                        )
                    }
                }

                onBack != null -> {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.chat_back),
                        )
                    }
                }
            }
        },
        actions = {
            if (selectedCount == null && header != null) ChatActions(header, onSearch, onSecretChatEvent, topicAction)
        },
        expandedHeight = titleHeight(),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colors.surfaceContainer,
                scrolledContainerColor = colors.surfaceContainer,
            ),
    )
}

/**
 * The default height, or the height of the two lines of the title with some room when they need more; also the search
 * field's, which takes the bar's place.
 */
@Composable
internal fun titleHeight(): Dp {
    val typography = MaterialTheme.typography
    val lines =
        with(LocalDensity.current) {
            typography.titleMediumEmphasized.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp()
        }
    return max(TopAppBarDefaults.TopAppBarExpandedHeight, lines + MaterialTheme.spacing.large)
}

/** "3 selected", a heading whose changes screen readers announce. */
@Composable
private fun SelectionTitle(count: Int) {
    Text(
        text = LocalResources.current.getQuantityString(R.plurals.selection_count, count, count),
        modifier =
            Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        maxLines = 1,
        style = MaterialTheme.typography.titleLarge,
    )
}

/**
 * The avatar beside the name and the subtitle; one heading for screen readers, a button that opens the profile where
 * there is one ([onOpenProfile]), or the info page of a group or channel ([onOpenChatInfo]). While someone is doing
 * something in the chat, or while [connection] is not ready, whose state then replaces the subtitle, the heading is a
 * polite live region, so screen readers announce it ("Grace is typing…", "Connecting…") when it starts or changes; the
 * status is not, as "last seen" changes every minute.
 */
@Composable
private fun ChatTitle(
    header: ChatHeader,
    connection: ConnectionStatus,
    onOpenProfile: ((userId: Long) -> Unit)?,
    onOpenChatInfo: ((chatId: Long) -> Unit)?,
) {
    val title = header.title.resolve()
    val connecting = connection.labelRes()
    val isAction = header.subtitle is ChatSubtitle.Action
    val userId = header.profileUserId
    val openLabel = stringResource(R.string.chat_open_profile)
    val infoLabel = stringResource(R.string.chat_open_info)
    val openInfo = onOpenChatInfo.takeIf { header.opensChatInfo }
    Row(
        modifier =
            Modifier
                .then(
                    when {
                        userId != null && onOpenProfile != null -> {
                            Modifier.clickable(onClickLabel = openLabel, role = Role.Button) { onOpenProfile(userId) }
                        }

                        openInfo != null -> {
                            Modifier.clickable(onClickLabel = infoLabel, role = Role.Button) { openInfo(header.chatId) }
                        }

                        else -> {
                            Modifier
                        }
                    },
                ).semantics(mergeDescendants = true) {
                    heading()
                    if (isAction || connecting != null) liveRegion = LiveRegionMode.Polite
                },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            photo = header.photo,
            name = title,
            id = header.chatId,
            size = MaterialTheme.sizes.avatarSmall,
            // It grows into the profile or info page the title opens (docs/DESIGN_SYSTEM.md §5).
            modifier =
                Modifier.avatarTransform(avatarTransformKey(userId.takeIf { onOpenProfile != null }, openInfo, header)),
            placeholderIcon = header.placeholderIcon,
        )
        Column {
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
            ChatSubtitleOrConnection(header.subtitle, connecting)
        }
    }
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

/** What TDLib is doing ([connecting], while not connected) in `onSurfaceVariant`, else the chat's [subtitle]. */
@Composable
private fun ChatSubtitleOrConnection(
    subtitle: ChatSubtitle?,
    @StringRes connecting: Int?,
) {
    if (connecting != null) {
        SubtitleText(stringResource(connecting), MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        subtitle?.let { ChatSubtitleLine(it) }
    }
}

/** The status in `onSurfaceVariant`, or what someone is doing in `primary` after the [TypingDots]. */
@Composable
private fun ChatSubtitleLine(subtitle: ChatSubtitle) {
    val text = subtitle.text.resolve()
    when (subtitle) {
        is ChatSubtitle.Status -> {
            SubtitleText(text, MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is ChatSubtitle.Action -> {
            val primary = MaterialTheme.colorScheme.primary
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TypingDots(color = primary, style = MaterialTheme.typography.bodySmall)
                // A name in the string, isolated: the string takes its language's direction, not the layout's.
                SubtitleText(text, primary, MaterialTheme.typography.bodySmall.userContent)
            }
        }
    }
}

@Composable
private fun SubtitleText(
    text: String,
    color: Color,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = style,
    )
}

/** What the subtitle says while not connected, as the chat list's pill does; null once connected. */
private fun ConnectionStatus.labelRes(): Int? =
    when (this) {
        ConnectionStatus.WaitingForNetwork -> R.string.chat_waiting_for_network
        ConnectionStatus.ConnectingToProxy -> R.string.chat_connecting_to_proxy
        ConnectionStatus.Connecting -> R.string.chat_connecting
        ConnectionStatus.Updating -> R.string.chat_updating
        ConnectionStatus.Ready -> null
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
