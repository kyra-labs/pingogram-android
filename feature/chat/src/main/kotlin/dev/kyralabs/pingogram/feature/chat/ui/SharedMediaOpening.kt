package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.ui.transition.SharedMediaShapes

/**
 * [onEvent], which first tells these shapes which media is asked to open ([ChatEvent.OpenMedia]), so its bubble readies
 * the transform into the viewer before the viewer composes ([SharedMediaShapes.opening]).
 */
internal fun SharedMediaShapes?.readyingOpenedMedia(onEvent: (ChatEvent) -> Unit): (ChatEvent) -> Unit =
    if (this == null) {
        onEvent
    } else {
        { event ->
            if (event is ChatEvent.OpenMedia) opening(event.messageId)
            onEvent(event)
        }
    }
