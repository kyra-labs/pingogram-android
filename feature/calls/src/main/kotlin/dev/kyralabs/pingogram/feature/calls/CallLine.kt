package dev.kyralabs.pingogram.feature.calls

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one call Pingogram runs at a time (`docs/adr/0018-calls-and-telecom.md`), shared by [CallManager] and
 * [GroupCallManager]: a one-to-one call and a voice chat never run together. Each manager takes the line under its own
 * lock and gives it back when its call is over.
 */
@Singleton
class CallLine
    @Inject
    constructor() {
        private var holder: Any? = null

        /** Takes the line for [owner]; false while someone else holds it. */
        @Synchronized
        fun take(owner: Any): Boolean {
            if (holder != null && holder !== owner) return false
            holder = owner
            return true
        }

        /** Gives the line back, if [owner] holds it. */
        @Synchronized
        fun release(owner: Any) {
            if (holder === owner) holder = null
        }

        @get:Synchronized
        val isBusy: Boolean get() = holder != null
    }
