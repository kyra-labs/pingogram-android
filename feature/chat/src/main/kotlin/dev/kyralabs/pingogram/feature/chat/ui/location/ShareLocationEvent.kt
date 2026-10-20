package dev.kyralabs.pingogram.feature.chat.ui.location

/** What the user and the screen do with the "Location" sheet, sent to [ShareLocationViewModel.onEvent]. */
sealed interface ShareLocationEvent {
    /** "Location" in the attach menu: the sheet opens, unless the device's location is off. */
    data object Open : ShareLocationEvent

    /**
     * The screen is started again, e.g. back from the system settings: what the user allowed there, or the location
     * turned on there, applies.
     */
    data object Started : ShareLocationEvent

    /** The explanation's button: Android asks for the location, or the app's settings open once it no longer asks. */
    data object Allow : ShareLocationEvent

    /** "Use precise location" while only an approximate one is allowed: Android asks again. */
    data object AskPrecise : ShareLocationEvent

    /**
     * Android answered the request for the location; [canAskAgain] whether it would ask again
     * (`shouldShowRequestPermissionRationale`), false once the user refused for good.
     */
    data class PermissionResult(
        val canAskAgain: Boolean,
    ) : ShareLocationEvent

    /** "Try again" after the location was not found. */
    data object Retry : ShareLocationEvent

    /** "Open in maps": the location found opens in a maps app. */
    data object OpenInMaps : ShareLocationEvent

    /** The sheet was dismissed, or its location sent: the search stops. */
    data object Dismiss : ShareLocationEvent
}
