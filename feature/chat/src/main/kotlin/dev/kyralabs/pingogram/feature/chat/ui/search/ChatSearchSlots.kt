package dev.kyralabs.pingogram.feature.chat.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.rememberSelectionBack
import dev.kyralabs.pingogram.core.ui.theme.predictiveBackScale
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatSearchEvent

/**
 * The conversation's top bar, or while [searching] the search field in its place ([ChatSearchTopBar], as tall as the
 * bar: [height]), the two cross-fading on the motion scheme's effects springs. [onClose] is the field's back button.
 * The field takes the focus when the user opens search, not when it shows from the start (after a rotation or process
 * death), so the keyboard does not open by itself then.
 */
@Composable
internal fun SearchTopBarSlot(
    searching: Boolean,
    query: TextFieldState,
    height: Dp,
    onClose: () -> Unit,
    topBar: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    // Not snapshot state: only the composition that shows the field reads it, after this one set it.
    val closedBefore = remember { ClosedBefore() }
    if (!searching) closedBefore.value = true
    AnimatedContent(
        targetState = searching,
        transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
        label = "searchTopBar",
    ) { shown ->
        if (shown) ChatSearchTopBar(query, height, onClose, focusOnShow = closedBefore.value) else topBar()
    }
}

/**
 * The composer, or while search is open ([search] not null) the search's bar in its place ([ChatSearchBottomBar]),
 * which slides up and fades in as the composer fades out, and back again, as the selection toolbar does, the bar's
 * height following on the default spatial spring. It shrinks with a predictive back gesture ([backProgress], 0 to 1)
 * that closes search.
 */
@Composable
internal fun SearchBottomBarSlot(
    search: ChatSearchUiState?,
    onEvent: (ChatEvent) -> Unit,
    backProgress: () -> Float,
    composer: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = search,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            val barIn = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec())
            val barOut = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec())
            val transform =
                if (targetState != null) {
                    barIn togetherWith fadeOut(motion.fastEffectsSpec())
                } else {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith barOut
                }
            transform using SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        contentAlignment = Alignment.BottomCenter,
        contentKey = { it != null },
        label = "searchBottomBar",
    ) { shown ->
        if (shown != null) {
            ChatSearchBottomBar(shown, onEvent, Modifier.predictiveBackScale(backProgress))
        } else {
            composer()
        }
    }
}

/** Whether the top bar showed without search before, since which opening search focuses its field. */
private class ClosedBefore {
    var value = false
}

/**
 * Closes search with back while it is [open] ([ChatSearchEvent.Close]), and the gesture's progress for its bar to
 * follow; not while [selecting]: selection mode shows over search, and back leaves it first.
 */
@Composable
internal fun rememberSearchBack(
    open: Boolean,
    selecting: Boolean,
    onEvent: (ChatEvent) -> Unit,
): Animatable<Float, AnimationVector1D> =
    rememberSelectionBack(enabled = open && !selecting) { onEvent(ChatSearchEvent.Close) }
