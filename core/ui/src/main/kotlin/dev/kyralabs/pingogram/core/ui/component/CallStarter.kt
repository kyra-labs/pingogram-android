package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Calls a user (`REQUIREMENTS.md` FR-V1): what the call buttons of the conversation and the profile and a call's
 * service message ("Call back") do. `:app` provides it from `feature:calls`, which features do not depend on, as links
 * go through `LocalUriHandler`.
 */
fun interface CallStarter {
    fun call(userId: Long)
}

/** The app's [CallStarter]; null in previews and tests, where the call buttons are left out. */
val LocalCallStarter = staticCompositionLocalOf<CallStarter?> { null }
