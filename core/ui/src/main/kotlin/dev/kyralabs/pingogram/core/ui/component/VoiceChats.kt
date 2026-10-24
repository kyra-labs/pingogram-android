package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.coroutines.flow.Flow
import java.text.NumberFormat

/**
 * Voice chats and live streams (`docs/adr/0023-group-voice-chats-and-live-streams.md`): what a chat's screens (the
 * conversation's bar, the info page's row) show of a chat's voice chat and how they join or start one. `:app` provides
 * it from `feature:calls`, which features do not depend on, as [CallStarter].
 */
interface VoiceChats {
    /** Chat [chatId]'s voice chat, then every change; null while the chat has none. */
    fun voiceChat(chatId: Long): Flow<VoiceChatSummary?>

    /** Joins chat [chatId]'s voice chat and opens its screen; opens the screen if the user is in it already. */
    fun join(chatId: Long)

    /** Starts a voice chat in chat [chatId], as an administrator who may, and joins it. */
    fun start(chatId: Long)

    /**
     * Opens the screen of the conference call of [link] (a `t.me/call` link, `docs/adr/0025-conference-calls.md`),
     * which asks before it joins.
     */
    fun joinConference(link: String)
}

/** What a chat's screens show of its voice chat. */
@Immutable
data class VoiceChatSummary(
    /** How many are in it; null while TDLib has not said. */
    val participantCount: Int?,
    /** A channel's live stream: watched, not spoken in. */
    val isLiveStream: Boolean,
    /** The user is in it on this device. */
    val isJoined: Boolean,
)

/** The app's [VoiceChats]; null in previews and tests, where voice chats are left out. */
val LocalVoiceChats = staticCompositionLocalOf<VoiceChats?> { null }

/**
 * The bar of chat [chatId]'s voice chat at the top of its conversation (`docs/DESIGN_SYSTEM.md` §6, "Voice chat bar"),
 * above the pinned messages bar and inset as it is, by [padding] and `spacing.small` at the sides; it grows in while
 * the chat has a voice chat and shrinks away when it ends. Nothing without [LocalVoiceChats].
 */
@Composable
fun VoiceChatBarSlot(
    chatId: Long,
    padding: PaddingValues,
) {
    val voiceChats = LocalVoiceChats.current ?: return
    val summary by remember(voiceChats, chatId) { voiceChats.voiceChat(chatId) }.collectAsStateWithLifecycle(null)
    val last = remember { mutableStateOf(summary) }
    SideEffect { if (summary != null) last.value = summary }
    val shown = summary ?: last.value
    val motion = MaterialTheme.motionScheme
    val direction = LocalLayoutDirection.current
    val small = MaterialTheme.spacing.small
    AnimatedVisibility(
        visible = summary != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        shown?.let {
            VoiceChatBar(
                summary = it,
                onJoin = { voiceChats.join(chatId) },
                modifier =
                    Modifier.padding(
                        start = padding.calculateStartPadding(direction) + small,
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(direction) + small,
                    ),
            )
        }
    }
}

/**
 * A tonal strip on `surfaceContainerHigh` with `large` corners: the voice chat's symbol in `primary`, "Voice chat" (or
 * "Live stream") in `labelLarge` `primary` over how many are in it in `bodyMedium` `onSurfaceVariant`, then a
 * `FilledTonalButton` "Join" ("Open" once the user is in it). The whole strip joins; screen readers hear one button.
 */
@Composable
fun VoiceChatBar(
    summary: VoiceChatSummary,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val title =
        stringResource(if (summary.isLiveStream) R.string.voice_chat_bar_live_stream else R.string.voice_chat_bar_title)
    val count =
        summary.participantCount?.let { count ->
            val number = NumberFormat.getIntegerInstance().format(count)
            if (summary.isLiveStream) {
                pluralStringResource(R.plurals.voice_chat_bar_watching, count, number)
            } else {
                pluralStringResource(R.plurals.voice_chat_bar_participants, count, number)
            }
        }
    val action = stringResource(if (summary.isJoined) R.string.voice_chat_bar_open else R.string.voice_chat_bar_join)
    val description = listOfNotNull(title, count).joinToString()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceContainerHigh)
                .clickable(onClick = onJoin)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick(label = action) {
                        onJoin()
                        true
                    }
                }.padding(start = spacing.medium, end = spacing.small, top = spacing.small, bottom = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (summary.isLiveStream) R.drawable.ic_live_tv else R.drawable.ic_graphic_eq),
            contentDescription = null,
            tint = colors.primary,
        )
        Spacer(Modifier.width(spacing.medium))
        BarText(title, count, Modifier.weight(1f))
        Spacer(Modifier.width(spacing.small))
        FilledTonalButton(onClick = onJoin, shapes = ButtonDefaults.shapes()) {
            Text(action, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "Voice chat" in `labelLarge` `primary` over the count in `bodyMedium` `onSurfaceVariant`, one line each. */
@Composable
private fun BarText(
    title: String,
    count: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count != null) {
            Text(
                count,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
