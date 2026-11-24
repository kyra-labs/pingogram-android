package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.kyralabs.pingogram.core.domain.model.DoubleTapAction

/**
 * A bubble's taps where nothing inside takes them (links, media, quotes and badges keep theirs): a tap shows or hides
 * its time where [toggleTime] ([ChatEvent.ToggleTime]), and a second tap within the double-tap timeout calls
 * [onDoubleTap] where there is one. The first tap acts at once, never waiting to see whether a second follows; a
 * double tap then toggles the time back, so it leaves the time as it was and only reacts. Presses held for the
 * long-press timeout are no taps (the row's long press opens the actions), and selection mode takes the row's taps
 * before the bubble sees them.
 */
internal fun Modifier.bubbleTaps(
    messageId: Long,
    toggleTime: Boolean,
    onDoubleTap: (() -> Unit)?,
    onEvent: (ChatEvent) -> Unit,
): Modifier {
    if (!toggleTime && onDoubleTap == null) return this
    return pointerInput(messageId, toggleTime, onDoubleTap, onEvent) {
        awaitEachGesture {
            awaitTap() ?: return@awaitEachGesture
            if (toggleTime) onEvent(ChatEvent.ToggleTime(messageId))
            if (onDoubleTap == null) return@awaitEachGesture
            withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) { awaitFirstDown() }
                ?: return@awaitEachGesture
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                ?.consume()
                ?: return@awaitEachGesture
            if (toggleTime) onEvent(ChatEvent.ToggleTime(messageId))
            onDoubleTap()
        }
    }
}

/** A press released within the long-press timeout and not taken by anything else; its release consumed. */
private suspend fun AwaitPointerEventScope.awaitTap(): PointerInputChange? {
    awaitFirstDown()
    return withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
        ?.also { it.consume() }
}

/**
 * What a double tap on [bubble] does, as Settings → Chats → Double-tap to react says for its side (FR-X15): React adds
 * the quick reaction ([LocalDoubleTapReaction]) to its message, or takes it back where the user already reacted with
 * it, as tapping its badge does (a toggle haptic, the badge's crossfade in [toggles], [ChatEvent.ToggleReaction]);
 * Reply replies to it from the composer ([ChatEvent.Reply], a `Confirm` haptic). Null where a double tap does nothing:
 * Nothing, React before TDLib announced the quick reaction, or a message not on Telegram yet (sending or failed).
 * Telegram refuses a reaction the chat does not allow, which shows as the reactions' usual error.
 */
@Composable
internal fun rememberDoubleTapReaction(
    bubble: ChatItem.Bubble,
    toggles: ReactionToggleState,
    onEvent: (ChatEvent) -> Unit,
): (() -> Unit)? {
    val sent = bubble.status != OutgoingStatus.Pending && bubble.status != OutgoingStatus.Failed
    val doubleTap = LocalDoubleTapReaction.current
    val haptics = LocalHapticFeedback.current
    return when (doubleTap.on(bubble.isOutgoing).takeIf { sent }) {
        DoubleTapAction.React -> {
            doubleTap.reaction?.let { rememberReactionToggle(bubble, it, toggles, onEvent) }
        }

        DoubleTapAction.Reply -> {
            remember(bubble.id, onEvent, haptics) {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onEvent(ChatEvent.Reply(bubble.id))
                }
            }
        }

        DoubleTapAction.Nothing, null -> {
            null
        }
    }
}

/** Adding [label] to [bubble]'s message, or taking it back, with its haptic and the badge's crossfade. */
@Composable
private fun rememberReactionToggle(
    bubble: ChatItem.Bubble,
    label: ReactionLabel,
    toggles: ReactionToggleState,
    onEvent: (ChatEvent) -> Unit,
): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val messageId = bubble.reactionsMessageId
    val chip = bubble.reactions.firstOrNull { it.label == label }
    return remember(messageId, label, chip, toggles, onEvent, haptics) {
        {
            val chosen = chip?.isChosen == true
            haptics.performHapticFeedback(if (chosen) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            if (chip != null) toggles.toggle(messageId, chip)
            onEvent(ChatEvent.ToggleReaction(messageId, label))
        }
    }
}
