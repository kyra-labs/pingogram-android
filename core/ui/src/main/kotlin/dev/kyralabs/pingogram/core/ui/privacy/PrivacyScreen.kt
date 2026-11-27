package dev.kyralabs.pingogram.core.ui.privacy

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the privacy screen is on (Privacy and security → "Privacy screen", `REQUIREMENTS.md` FR-X25), provided by
 * the app's activity before its first frame, so no message shows for a frame before it is hidden; off by default, as
 * in previews and tests. Only the bubbles read it, so turning it on recomposes them alone.
 */
val LocalPrivacyScreen: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }
