package dev.kyralabs.pingogram.feature.calls

/**
 * What [CallManager] and [GroupCallManager] do to the device around a call, behind small seams so their tests run on
 * the JVM:
 * the foreground service ([CallForeground]), the ringback and busy tones ([CallTones]) and the clock ([CallClock]).
 */
interface CallForeground {
    /**
     * Keeps the app running with the call's ongoing notification, while the call rings or goes on. [withMicrophone]
     * once the user is talking: Android lets the service use the microphone in the background only from then on.
     */
    fun start(withMicrophone: Boolean)

    fun stop()
}

/** The tones the caller hears from their own phone. */
interface CallTones {
    /** The other person's phone rings: the ringback tone, until [stop]. */
    fun ringback()

    /** The other person declined or is busy: the busy tone, for a moment. */
    fun busy()

    fun stop()
}

/** Time since boot in milliseconds, which goes on while the device sleeps: a call's duration. */
fun interface CallClock {
    fun elapsedRealtimeMillis(): Long
}

/** The name of a user as the system's UI, a car or a watch shows it. */
fun interface CallerNames {
    suspend fun name(userId: Long): String
}

/** The title of a voice chat's chat, as the system's UI, a car or a watch shows it. */
fun interface ChatTitles {
    suspend fun title(chatId: Long): String
}
