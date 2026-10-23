package dev.kyralabs.pingogram.background

/**
 * Keeps new messages arriving while Pingogram is in the background, as far as the build's flavor can
 * (`PushDelivery`): in `foss` the optional "Keep connected" foreground service (`KeepConnectedController`); in `gms`
 * Firebase Cloud Messaging's registration (`FirebasePushConnection`, `docs/adr/0004-push-delivery.md`).
 * `PingogramApplication` calls [start] once per process.
 */
interface BackgroundConnection {
    /** Starts following what it depends on; returns at once. */
    fun start()
}
