package dev.kyralabs.pingogram.feature.chat.ui

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
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.secret.selfDestructShortText
import dev.kyralabs.pingogram.feature.chat.ui.secret.selfDestructText
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The conversation's small [TopAppBar] (`docs/DESIGN_SYSTEM.md` §6): back, then the chat's avatar and a two-line
 * title, its name in `titleMediumEmphasized` over the subtitle in `bodySmall`: `onSurfaceVariant`, or `primary` with
 * pulsing [TypingDots] while someone is typing or doing something else in the chat. No actions in other chats yet
 * (calls come in M5, search in the chat in M6, and Material allows two at most). In a private or secret chat the
 * title opens the other user's profile through [onOpenProfile] (`REQUIREMENTS.md` FR-P2), its state layer over the
 * title; groups and channels keep it inert until chat info comes. A secret chat (`REQUIREMENTS.md` FR-E1,
 * [ChatHeader.secret]) has a lock before its name, and two actions ([SecretChatActions]): its self-destruct timer,
 * with the timer on a badge, and its encryption key, both through [onSecretChatEvent]. It sits on the screen's
 * `surfaceContainer`, above the sheet the messages scroll in. Without [onBack] there is no back button: in the detail
 * pane beside the chat list, which has nothing to go back to.
 *
 * It grows with the font scale instead of clipping its two lines, which the default height fits only up to about
 * 150%.
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
                if (count != null) SelectionTitle(count) else header?.let { ChatTitle(it, onOpenProfile) }
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
            val secret = header?.secret
            if (secret != null && selectedCount == null) SecretChatActions(secret, onSecretChatEvent)
        },
        expandedHeight = titleHeight(),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colors.surfaceContainer,
                scrolledContainerColor = colors.surfaceContainer,
            ),
    )
}

/** The default height, or the height of the two lines of the title with some room when they need more. */
@Composable
private fun titleHeight(): Dp {
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
 * there is one ([onOpenProfile]). While someone is doing something in the chat, the heading is a polite live region,
 * so screen readers announce it ("Grace is typing…") when it starts or changes; the status is not, as "last seen"
 * changes every minute.
 */
@Composable
private fun ChatTitle(
    header: ChatHeader,
    onOpenProfile: ((userId: Long) -> Unit)?,
) {
    val title = header.title.resolve()
    val isAction = header.subtitle is ChatSubtitle.Action
    val userId = header.profileUserId
    val openLabel = stringResource(R.string.chat_open_profile)
    Row(
        modifier =
            Modifier
                .then(
                    if (userId != null && onOpenProfile != null) {
                        Modifier.clickable(onClickLabel = openLabel, role = Role.Button) { onOpenProfile(userId) }
                    } else {
                        Modifier
                    },
                ).semantics(mergeDescendants = true) {
                    heading()
                    if (isAction) liveRegion = LiveRegionMode.Polite
                },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            photo = header.photo,
            name = title,
            id = header.chatId,
            size = MaterialTheme.sizes.avatarSmall,
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
            header.subtitle?.let { ChatSubtitleLine(it) }
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

/**
 * The self-destruct timer and the encryption key of a secret chat, as filled tonal icon buttons with the expressive
 * press shapes (`docs/DESIGN_SYSTEM.md` §6, at most two). A timer that is on shows in short on a `primary` badge
 * ("30s"); the timer cannot change before the chat is ready or after it ended, and is disabled then.
 */
@Composable
private fun SecretChatActions(
    secret: SecretChatHeader,
    onEvent: (SecretChatEvent) -> Unit,
) {
    val resources = LocalResources.current
    val seconds = secret.selfDestructSeconds
    val timerLabel =
        if (seconds > 0) {
            stringResource(R.string.secret_timer_button, selfDestructText(resources, seconds))
        } else {
            stringResource(R.string.secret_timer_button_off)
        }
    // The badge over the button's top end, inside its touch target, so it never covers the next button; not inside
    // the button, which clips its content to its shape.
    Box {
        FilledTonalIconButton(
            onClick = { onEvent(SecretChatEvent.OpenTimer) },
            enabled = secret.canSetTimer,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painter = painterResource(UiR.drawable.ic_timer), contentDescription = timerLabel)
        }
        if (seconds > 0) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(selfDestructShortText(resources, seconds), maxLines = 1)
            }
        }
    }
    FilledTonalIconButton(onClick = { onEvent(SecretChatEvent.OpenKey) }, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(UiR.drawable.ic_key),
            contentDescription = stringResource(R.string.secret_key_button),
        )
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
