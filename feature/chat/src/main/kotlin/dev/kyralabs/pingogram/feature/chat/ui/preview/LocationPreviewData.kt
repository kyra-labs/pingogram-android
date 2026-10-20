package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.feature.chat.ui.location.ShareLocationUiState

/** Sample states of the "Location" sheet for previews and screenshot tests, around the Science Museum in London. */
internal object LocationPreviewData {
    private val museum = Location(51.497810, -0.174530)

    /** A precise location, accurate enough that the search stops saying it improves. */
    val found = ShareLocationUiState.Locating(DeviceLocation(museum, accuracyMeters = 8f))

    /** The last location Android knew, while a better one is searched for. */
    val refining = ShareLocationUiState.Locating(DeviceLocation(museum, accuracyMeters = 65f, isCached = true))

    /** Only an approximate location allowed (Android 12+). */
    val approximate =
        ShareLocationUiState.Locating(DeviceLocation(museum, accuracyMeters = 2_000f), isApproximate = true)
}
