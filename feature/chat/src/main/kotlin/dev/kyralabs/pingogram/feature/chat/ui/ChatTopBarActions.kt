package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Badge
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.LocalCallStarter
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.secret.selfDestructShortText
import dev.kyralabs.pingogram.feature.chat.ui.secret.selfDestructText
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The actions of the conversation's top bar for [header]'s chat (`docs/DESIGN_SYSTEM.md` §6, at most two): search
 * ([onSearch], `REQUIREMENTS.md` §5.9) in every chat, then a secret chat's self-destruct timer ([SecretChatActions],
 * through [onSecretChatEvent]), or the call button of a private chat with a person ([ChatHeader.callUserId], through
 * [LocalCallStarter]). Without search, a secret chat's encryption key takes its place.
 */
@Composable
internal fun ChatActions(
    header: ChatHeader,
    onSearch: (() -> Unit)?,
    onSecretChatEvent: (SecretChatEvent) -> Unit,
) {
    val secret = header.secret
    val callUserId = header.callUserId
    val calls = LocalCallStarter.current
    onSearch?.let { SearchAction(it) }
    when {
        secret != null -> SecretChatActions(secret, onSecretChatEvent, showsKey = onSearch == null)
        callUserId != null && calls != null -> CallAction { calls.call(callUserId) }
    }
}

/**
 * The self-destruct timer of a secret chat, and its encryption key where [showsKey], as filled tonal icon buttons with
 * the expressive press shapes (`docs/DESIGN_SYSTEM.md` §6, at most two). A timer that is on shows in short on a
 * `primary` badge ("30s"); the timer cannot change before the chat is ready or after it ended, and is disabled then.
 */
@Composable
private fun SecretChatActions(
    secret: SecretChatHeader,
    onEvent: (SecretChatEvent) -> Unit,
    showsKey: Boolean,
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
    if (showsKey) {
        FilledTonalIconButton(onClick = { onEvent(SecretChatEvent.OpenKey) }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_key),
                contentDescription = stringResource(R.string.secret_key_button),
            )
        }
    }
}

/** Opens the search in the chat (`REQUIREMENTS.md` §5.9), a filled tonal icon button as the others. */
@Composable
private fun SearchAction(onSearch: () -> Unit) {
    FilledTonalIconButton(onClick = onSearch, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(UiR.drawable.ic_search),
            contentDescription = stringResource(R.string.search_open),
        )
    }
}

/** Calls the other side of a private chat (`REQUIREMENTS.md` FR-V1), a filled tonal icon button as the others. */
@Composable
private fun CallAction(onCall: () -> Unit) {
    FilledTonalIconButton(onClick = onCall, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(UiR.drawable.ic_call_filled),
            contentDescription = stringResource(R.string.chat_call),
        )
    }
}
