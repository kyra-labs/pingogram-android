package dev.kyralabs.pingogram.background

import javax.inject.Inject

/**
 * The `gms` flavor's [BackgroundConnection]: nothing to keep running. Firebase Cloud Messaging will wake the app for
 * new messages (`REQUIREMENTS.md` FR-N3), so this flavor ships no foreground service.
 */
class NoBackgroundConnection
    @Inject
    constructor() : BackgroundConnection {
        override fun start() = Unit
    }
