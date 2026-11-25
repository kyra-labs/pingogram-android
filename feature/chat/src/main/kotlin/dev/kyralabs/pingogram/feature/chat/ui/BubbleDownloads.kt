package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * How the bubbles' photos download ([MediaDownloads]), as the bubbles read it: one object for as long as the list
 * shows, whose two fields are snapshot state. A download the user asks for changes [isRequested] alone, which only the
 * photos showing a download button read, so it recomposes those and leaves every other bubble, and the resources the
 * bubbles share ([BubbleResources]), as they are.
 */
@Stable
internal class BubbleDownloads(
    downloads: MediaDownloads = MediaDownloads(),
) {
    /** Whether photos download by themselves on the current network ([MediaDownloads.photos]). */
    var photos: Boolean by mutableStateOf(downloads.photos)
        private set

    private var requested by mutableStateOf(downloads.requested)

    /** Whether the user asked for file [fileId] ([MediaDownloads.requested]). */
    fun isRequested(fileId: Int): Boolean = fileId in requested

    /** Takes what [downloads] says; a field that stays equal changes nothing. */
    fun update(downloads: MediaDownloads) {
        photos = downloads.photos
        requested = downloads.requested
    }

    companion object {
        /** Photos download by themselves, none asked for: what a bubble outside a conversation shows. */
        val Automatic = BubbleDownloads()
    }
}

/**
 * Whether the user asked for file [fileId] ([BubbleDownloads.isRequested]), as a state whose reader recomposes only
 * when that answer changes: a download asked for recomposes the photo of that file alone.
 */
@Composable
internal fun BubbleDownloads.requestedState(fileId: Int): State<Boolean> =
    remember(this, fileId) { derivedStateOf { isRequested(fileId) } }
