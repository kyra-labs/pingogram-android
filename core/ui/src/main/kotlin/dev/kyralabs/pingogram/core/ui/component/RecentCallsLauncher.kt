package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens Recent calls (`:feature:calls`) from a screen of another feature, which cannot depend on it: Settings home and
 * the Contacts tab show their entry only where the app provides one. Null in previews and tests.
 */
val LocalRecentCallsLauncher = staticCompositionLocalOf<(() -> Unit)?> { null }
