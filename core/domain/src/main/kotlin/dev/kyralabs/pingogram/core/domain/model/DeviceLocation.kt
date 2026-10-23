package dev.kyralabs.pingogram.core.domain.model

/**
 * Where the device is, as Android measured it (`REQUIREMENTS.md` FR-C5 "location"): [location] within [accuracyMeters]
 * of it (the radius Android reports, null where it reports none). [isCached] for the last location Android knew when
 * the search started, which a fresh measurement then replaces. `toString()` leaves the place out.
 */
data class DeviceLocation(
    val location: Location,
    val accuracyMeters: Float?,
    val isCached: Boolean = false,
) {
    override fun toString() = "DeviceLocation(accuracyMeters=$accuracyMeters, isCached=$isCached, …)"
}

/** How much of the device's location the user lets Pingogram know: nothing, roughly (Android 12+), or precisely. */
enum class LocationAccess {
    None,
    Approximate,
    Precise,
}
