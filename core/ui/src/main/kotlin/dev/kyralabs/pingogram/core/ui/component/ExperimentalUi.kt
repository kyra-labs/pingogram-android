package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the app shows its experimental look (Settings → "Experimental UI", `docs/adr/0035-experimental-ui.md`,
 * `docs/DESIGN_SYSTEM.md` §13): one UI with style variants, never a copy of a screen. `MainActivity` reads the setting
 * with the Appearance settings, before the first frame, and provides it once per activity, so the app never draws one
 * look and then the other. A static local: turning it on or off composes the app again in place, keeping its screens'
 * state, as the rare change it is. False in previews and tests unless they provide it.
 */
val LocalExperimentalUi: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }
