package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.ui.transition.FullBleedShape
import dev.kyralabs.pingogram.core.ui.transition.containerTransform
import dev.kyralabs.pingogram.feature.chats.ui.NewChatTransformKey

/**
 * The "New chat" button's side of its container transform into the New chat screen (`docs/DESIGN_SYSTEM.md` §5, §6
 * "New chat"; owner decision 2026-11-06): the app shell's button beside the floating navigation bar or in the rail's
 * header, clipped to [shape]. The button and the screen share their bounds while the screen's destination comes or
 * goes and the button's own animated visibility scope goes or comes with it: the shell hides the button while New
 * chat shows, so the two never show at once. The screen's corners are square, the button's [shape].
 */
@Composable
fun Modifier.newChatButtonTransform(shape: CornerBasedShape): Modifier =
    containerTransform(NewChatTransformKey, shape = shape, counterpartShape = FullBleedShape)
