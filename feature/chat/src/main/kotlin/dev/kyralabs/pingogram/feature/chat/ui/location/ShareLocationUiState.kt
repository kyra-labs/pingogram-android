package dev.kyralabs.pingogram.feature.chat.ui.location

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation

/** What the "Location" sheet of the attach menu shows (`REQUIREMENTS.md` FR-C5), from [ShareLocationViewModel]. */
@Immutable
sealed interface ShareLocationUiState {
    /** No sheet. */
    data object Closed : ShareLocationUiState

    /**
     * Why Pingogram asks for the location, before Android asks; [deniedForGood] once Android no longer asks, so the
     * sheet opens the app's settings instead.
     */
    data class Permission(
        val deniedForGood: Boolean = false,
    ) : ShareLocationUiState

    /**
     * Searching for the location, showing the best one found so far ([location], null until the first). With
     * [isApproximate] the user allowed only an approximate location (Android 12+), which is what is sent.
     */
    data class Locating(
        val location: DeviceLocation? = null,
        val isApproximate: Boolean = false,
    ) : ShareLocationUiState {
        /**
         * Whether a better location is still likely: while only the last known one shows, or a precise one is rougher
         * than [GOOD_ACCURACY_METERS]. An approximate location never gets better.
         */
        val isRefining: Boolean
            get() =
                location == null ||
                    (
                        !isApproximate &&
                            (location.isCached || (location.accuracyMeters ?: Float.MAX_VALUE) > GOOD_ACCURACY_METERS)
                    )
    }

    /** No location within the time the search takes, or no provider that could give one. */
    data object NotFound : ShareLocationUiState
}

/** Accurate enough to stop saying the location is being improved: about a street's width. */
internal const val GOOD_ACCURACY_METERS = 20f
