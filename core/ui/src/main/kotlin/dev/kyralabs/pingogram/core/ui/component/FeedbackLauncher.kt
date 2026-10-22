package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens "Send feedback" (`docs/TESTING_GUIDE.md`, `:feature:feedback`) from a screen of another feature, which cannot
 * depend on it: Settings → About shows its row only where the app provides one. Null in previews and tests.
 */
val LocalFeedbackLauncher = staticCompositionLocalOf<(() -> Unit)?> { null }
