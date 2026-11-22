package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * What sits over the messages' bottom edge: the typing bubble ([TypingRowSlot]), for which the list keeps room below
 * the newest message while it shows. The messages scroll on under the floating composer, with no fade into it.
 */
@Composable
internal fun BoxScope.MessagesBottom(
    typing: TypingBubble?,
    contentPadding: PaddingValues,
) {
    TypingRowSlot(typing, contentPadding, Modifier.align(Alignment.BottomStart))
}
