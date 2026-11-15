package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/**
 * Whether the viewer's chrome shows; tapping the media toggles it. Saved, so it survives rotation. While a screen
 * reader explores by touch ([keptShown], board 25), it always shows and taps leave it.
 */
@Stable
internal class ChromeState(
    visible: Boolean = true,
) {
    private var shown by mutableStateOf(visible)

    /** Set while TalkBack explores by touch: the chrome never hides then. */
    var keptShown by mutableStateOf(false)

    val visible: Boolean get() = shown || keptShown

    fun toggle() {
        if (!keptShown) shown = !shown
    }

    companion object {
        val Saver: Saver<ChromeState, Boolean> = Saver(save = { it.shown }, restore = { ChromeState(it) })
    }
}

/** A tap on [item]'s page: shows its spoiler, or shows or hides [chrome], saying when it hid it. */
internal fun pageTap(
    item: MediaItem,
    chrome: ChromeState,
    onEvent: (MediaViewerEvent) -> Unit,
): () -> Unit =
    if (item.isHidden) {
        { onEvent(MediaViewerEvent.RevealSpoiler(item.messageId)) }
    } else {
        {
            chrome.toggle()
            if (!chrome.visible) onEvent(MediaViewerEvent.ControlsHidden)
        }
    }
