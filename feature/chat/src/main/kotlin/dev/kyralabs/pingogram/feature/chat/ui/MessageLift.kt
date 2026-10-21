package dev.kyralabs.pingogram.feature.chat.ui

import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.sizes

/** Where the row of long-pressed message [messageId] was in the window ([bounds]) when its actions were asked for. */
@Immutable
internal data class LiftAnchor(
    val messageId: Long,
    val bounds: Rect,
)

/**
 * The lifted message of the conversation's floating message menu (`docs/DESIGN_SYSTEM.md` §6 "Message actions"):
 * which message a long press lifted and where it was ([anchor]), and how far the menu shows ([visibility], from 0
 * hidden to 1 shown), which the conversation's blur and the menu's scrim follow in the draw phase.
 *
 * A long press lifts its message ([lift]) unless the actions belong in their bottom sheet: with a screen reader that
 * explores by touch (TalkBack), and in windows shorter than `sizes.messageMenuMinWindowHeight` (phones in landscape),
 * where the message, its reactions and its menu would not fit together. Without [accessibility] and [window] (previews,
 * tests), every long press lifts.
 */
@Stable
internal class MessageLiftState(
    anchor: LiftAnchor? = null,
    private val accessibility: AccessibilityManager? = null,
    private val window: WindowInfo? = null,
    private val minWindowHeight: Int = 0,
) {
    /** The lifted message, until its actions close; null when they open in their sheet. */
    var anchor by mutableStateOf(anchor)
        private set

    /** How far the floating menu shows, 0 to 1. Read in the draw phase only. */
    var visibility by mutableFloatStateOf(0f)

    /** Lifts message [messageId] from [bounds], its row in the window, where the floating menu suits the window. */
    fun lift(
        messageId: Long,
        bounds: Rect?,
    ) {
        anchor = bounds?.takeIf { canLift() }?.let { LiftAnchor(messageId, it) }
    }

    /** The menu is gone: nothing lifted, nothing blurred. */
    fun clear() {
        anchor = null
        visibility = 0f
    }

    private fun canLift(): Boolean {
        val touchExploration = accessibility?.run { isEnabled && isTouchExplorationEnabled } == true
        val height = window?.containerSize?.height ?: Int.MAX_VALUE
        return !touchExploration && height >= minWindowHeight
    }
}

/** The conversation's [MessageLiftState], aware of screen readers and of the window's height. */
@Composable
internal fun rememberMessageLiftState(): MessageLiftState {
    val context = LocalContext.current
    val window = LocalWindowInfo.current
    val minHeight = MaterialTheme.sizes.messageMenuMinWindowHeight
    val density = LocalDensity.current
    return remember(context, window, minHeight, density) {
        MessageLiftState(
            accessibility = context.getSystemService(AccessibilityManager::class.java),
            window = window,
            minWindowHeight = with(density) { minHeight.roundToPx() },
        )
    }
}

/**
 * Blurs this content behind the floating message menu by [radius] as far as [lift] shows it (`RenderEffect`, API 31+;
 * below, the scrim alone sets the message off), read in the draw phase, so it never recomposes.
 */
internal fun Modifier.blurBehindMenu(
    lift: MessageLiftState,
    radius: Dp,
): Modifier =
    graphicsLayer {
        val shown = lift.visibility
        if (shown > 0f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val px = radius.toPx() * shown
            renderEffect = BlurEffect(px, px, TileMode.Clamp)
            clip = true
        } else {
            renderEffect = null
            clip = false
        }
    }
