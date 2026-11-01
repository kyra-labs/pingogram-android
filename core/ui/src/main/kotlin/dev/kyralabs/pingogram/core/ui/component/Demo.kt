package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the demo shows (`docs/adr/0034-demo-mode-for-store-review.md`), provided by `MainActivity`, which reads it
 * once: the app opens again when the demo turns on or off, so it never changes during a composition. Screens leave out
 * what would change the phone or a real account there (Settings' other accounts, "Keep connected"), and Settings shows
 * "Demo" and "Leave demo".
 */
val LocalIsDemo: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }
