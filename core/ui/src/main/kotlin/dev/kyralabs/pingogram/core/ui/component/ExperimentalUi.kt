package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the app shows the look of `docs/DESIGN_SYSTEM.md` §13, which is Pingogram's only look since the owner's
 * decision of 2026-11-08 (`docs/adr/0035-experimental-ui.md`): true everywhere. The other look's code goes module by
 * module, and this local with it.
 */
val LocalExperimentalUi: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { true }
