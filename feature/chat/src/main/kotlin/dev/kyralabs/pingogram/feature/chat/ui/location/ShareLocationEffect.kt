package dev.kyralabs.pingogram.feature.chat.ui.location

import dev.kyralabs.pingogram.core.domain.model.DeviceLocation

/** One-off effects of [ShareLocationViewModel], handled by the conversation's route once each. */
sealed interface ShareLocationEffect {
    /** Ask Android for the location, precise or approximate as the user picks; the answer comes back as an event. */
    data object RequestPermission : ShareLocationEffect

    /** Open the app's page in the system settings, where a location refused for good is allowed. */
    data object OpenAppSettings : ShareLocationEffect

    /** The device's location is off: a snackbar says so, with an action that opens the location settings. */
    data object LocationOff : ShareLocationEffect

    /** Show [location] in a maps app. */
    data class OpenInMaps(
        val location: DeviceLocation,
    ) : ShareLocationEffect
}
