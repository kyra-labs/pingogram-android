package dev.kyralabs.pingogram.push

import android.content.Context
import com.google.firebase.FirebaseApp

/**
 * Starts Firebase where the app first needs it, instead of in Firebase's own content provider, which would run on the
 * main thread before `PingogramApplication.onCreate` in every process (`docs/PERFORMANCE.md` §2): the flavor's manifest
 * removes that provider. Firebase is needed only for a push token (on the background starters, at login) and in the
 * messaging service, which Firebase starts for a push or a new token.
 */
internal object FirebaseStart {
    /** Starts Firebase if it has not started yet; nothing without `google-services.json`'s configuration. */
    fun ensure(context: Context) {
        FirebaseApp.initializeApp(context.applicationContext)
    }
}
