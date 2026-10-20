package dev.kyralabs.pingogram.background

/**
 * Keeps new messages arriving while Pingogram is in the background, as far as the build's flavor can
 * (`PushDelivery`): in `foss` the optional "Keep connected" foreground service (`KeepConnectedController`); in `gms`
 * nothing yet, until Firebase Cloud Messaging (`REQUIREMENTS.md` FR-N3). `PingogramApplication` calls [start] once per
 * process.
 */
interface BackgroundConnection {
    /** Starts following what it depends on; returns at once. */
    fun start()
}
