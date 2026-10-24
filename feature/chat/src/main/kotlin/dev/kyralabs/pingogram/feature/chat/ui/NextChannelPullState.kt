package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.NextChannelPreviewData

// Pulling a read channel up past its newest post opens the next unread channel (docs/DESIGN_SYSTEM.md §6).

/**
 * How far the conversation of a read channel is pulled up past its newest post ([pull], in pixels), toward [next], and
 * what the pull does: the list's [connection] takes what the list leaves of an upward drag at its newest post, at
 * [RESISTANCE], and gives it back first as the drag turns down; [isArmed] once past [threshold], with a
 * `TextHandleMove` haptic as it crosses; released there, [onOpen] opens the channel, and the pull settles back on
 * [settle] either way. A fling of a pull goes nowhere else. Read in the layout and draw phases ([pullAway],
 * [NextChannelIndicator]), so a pull recomposes nothing but the indicator's text as it arms. Previews start at
 * [initialPull].
 */
@Stable
internal class NextChannelPullState(
    next: NextChannel,
    private val threshold: Float,
    private val haptics: HapticFeedback,
    private val settle: AnimationSpec<Float>,
    private val onOpen: (chatId: Long) -> Unit,
    initialPull: Float = 0f,
) {
    /** The channel a release past [threshold] opens. */
    var next: NextChannel by mutableStateOf(next)

    /** How far the list is pulled, in pixels, at most [MAX_PULL] times [threshold]. */
    var pull: Float by mutableFloatStateOf(initialPull)
        private set

    /** How far toward [threshold] the list is pulled, 0 to 1. */
    val progress: Float get() = (pull / threshold).coerceIn(0f, 1f)

    /** Pulled past [threshold]: a release opens [next]. */
    val isArmed: Boolean by derivedStateOf { pull >= threshold }

    /** Opens [next] at once, for screen readers, which do not pull. */
    fun openNow() = onOpen(next.chatId)

    private fun pullTo(value: Float) {
        val wasArmed = pull >= threshold
        pull = value.coerceIn(0f, threshold * MAX_PULL)
        if (!wasArmed && pull >= threshold) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val connection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // A drag back down shrinks the pull before it scrolls the list.
                if (source != NestedScrollSource.UserInput || pull <= 0f || available.y <= 0f) return Offset.Zero
                val taken = minOf(available.y * RESISTANCE, pull)
                pullTo(pull - taken)
                return Offset(0f, taken / RESISTANCE)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // What an upward drag leaves at the newest post (the reverse list's start) pulls.
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                pullTo(pull - available.y * RESISTANCE)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull <= 0f) return Velocity.Zero
                if (isArmed) onOpen(next.chatId)
                animate(pull, 0f, animationSpec = settle) { value, _ -> pull = value }
                return available
            }
        }

    companion object {
        /** How much of the finger's movement the pull follows. */
        const val RESISTANCE = 0.5f

        /** How far past the threshold the list may be pulled, as a multiple of it. */
        const val MAX_PULL = 1.25f
    }
}

/**
 * The pull toward [next] (null: none, nothing pulls), which opens it through [onEvent] ([ChatEvent.OpenNextChannel]);
 * kept while the conversation shows, following the latest [next].
 */
@Composable
internal fun rememberNextChannelPull(
    next: NextChannel?,
    onEvent: (ChatEvent) -> Unit,
): NextChannelPullState? {
    if (next == null) return null
    val threshold = with(LocalDensity.current) { MaterialTheme.sizes.nextChatPullDistance.toPx() }
    val haptics = LocalHapticFeedback.current
    val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val state =
        remember(threshold, haptics, settle) {
            NextChannelPullState(
                next,
                threshold,
                haptics,
                settle,
                onOpen = { currentOnEvent(ChatEvent.OpenNextChannel(it)) },
            )
        }
    state.next = next
    return state
}

/**
 * The list of a read channel's conversation with its [pull]: it takes the list's leftover drags
 * ([NextChannelPullState.connection]) and moves the list up by the pull in its graphics layer; screen readers open
 * the next channel through a custom action ("Open the next unread channel, Material Design").
 */
@Composable
internal fun Modifier.pullAway(pull: NextChannelPullState): Modifier {
    val resources = LocalResources.current
    return nestedScroll(pull.connection)
        .graphicsLayer { translationY = -pull.pull }
        .semantics {
            val label = resources.getString(R.string.chat_next_channel_action, pull.next.title)
            customActions =
                listOf(
                    CustomAccessibilityAction(label) {
                        pull.openNow()
                        true
                    },
                )
        }
}

/**
 * Below the pulled list: the next channel on a `surfaceContainerHigh` pill, its avatar in a determinate
 * `CircularWavyProgressIndicator` that fills as the pull nears its threshold, its name in `titleSmallEmphasized` over
 * "Pull up to open" or, armed, "Release to open" in `bodySmall` `onSurfaceVariant`. It rises with the pull from below
 * the list's bottom, the composer's place, and fades in with its progress, both in its graphics layer.
 * Screen readers skip it: the list's custom action opens the channel ([pullAway]).
 */
@Composable
internal fun NextChannelIndicator(
    pull: NextChannelPullState,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val sizes = MaterialTheme.sizes
    val next = pull.next
    Surface(
        modifier =
            modifier
                .widthIn(max = sizes.bubbleMaxWidth)
                .graphicsLayer {
                    alpha = pull.progress
                    translationY = (size.height - pull.pull).coerceAtLeast(0f)
                }.clearAndSetSemantics {},
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = spacing.small, end = spacing.large).padding(vertical = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(sizes.avatarRow), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { pull.progress }, modifier = Modifier.size(sizes.avatarRow))
                Avatar(photo = next.photo, name = next.title, id = next.chatId, size = sizes.avatarExtraSmall)
            }
            Column {
                Text(
                    text = next.title,
                    style = MaterialTheme.typography.titleSmallEmphasized.userContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            if (pull.isArmed) R.string.chat_next_channel_release else R.string.chat_next_channel_pull,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun NextChannelIndicatorPreview() {
    PingogramTheme(dynamicColor = false) {
        val threshold = with(LocalDensity.current) { MaterialTheme.sizes.nextChatPullDistance.toPx() }
        val settle = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val haptics = LocalHapticFeedback.current
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            listOf(threshold / 2, threshold).forEach { pull ->
                val state = NextChannelPullState(NextChannelPreviewData.next, threshold, haptics, settle, {}, pull)
                NextChannelIndicator(state)
            }
        }
    }
}
