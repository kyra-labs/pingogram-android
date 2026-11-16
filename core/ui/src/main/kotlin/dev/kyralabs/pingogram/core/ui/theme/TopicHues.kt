package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.kyralabs.pingogram.core.domain.model.TopicColor

/**
 * The sender hue ([SenderHues], `docs/DESIGN_SYSTEM.md` §13.6) a forum topic's color is drawn in (§2.2, owner decision
 * 2026-11-16): Telegram's six topic colors by the hue of their name, never Telegram's own colors, so all six differ in
 * every theme and contrast level. Blue → blue, yellow → gold, green → green, violet → violet, rose → pink, red → red.
 */
val TopicColor.senderHueIndex: Int
    get() =
        when (this) {
            TopicColor.Red -> SENDER_HUE_RED
            TopicColor.Violet -> SENDER_HUE_VIOLET
            TopicColor.Green -> SENDER_HUE_GREEN
            TopicColor.Blue -> SENDER_HUE_BLUE
            TopicColor.Rose -> SENDER_HUE_PINK
            TopicColor.Yellow -> SENDER_HUE_GOLD
        }

/** The theme's sender hue of a topic [color] ([senderHueIndex]). */
@Composable
@ReadOnlyComposable
fun topicHue(color: TopicColor): SenderHue = MaterialTheme.pingogramColors.senderHues[color.senderHueIndex]

// The sender hues' places, in `tools/theme`'s order (Telegram's seven built-in accent colors, then gold).
private const val SENDER_HUE_RED = 0
private const val SENDER_HUE_VIOLET = 2
private const val SENDER_HUE_GREEN = 3
private const val SENDER_HUE_BLUE = 5
private const val SENDER_HUE_PINK = 6
private const val SENDER_HUE_GOLD = 7
