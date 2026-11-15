package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the Bookmarks page (`:feature:chat`, the owner's board 34) from Settings › On this phone, which cannot depend
 * on it; Saved Messages' card uses it too. Settings shows its row only where the app provides one. Null in previews
 * and tests.
 */
val LocalBookmarksLauncher = staticCompositionLocalOf<(() -> Unit)?> { null }
