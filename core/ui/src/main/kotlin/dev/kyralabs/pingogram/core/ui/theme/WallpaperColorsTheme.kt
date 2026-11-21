package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.kyralabs.pingogram.core.domain.model.Contrast
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [content] in the colors of a chat's wallpaper (`docs/adr/0041-chat-colors-from-the-wallpaper.md`): the
 * [contentColorScheme] of [seed] in the enclosing [PingogramTheme]'s light or dark, contrast level and AMOLED black,
 * with the bubbles' and the conversation's colors ([PingogramColors]) made from it. A null [seed] (no wallpaper, or one
 * whose seed is not known yet) keeps the enclosing colors exactly. The scheme is computed once per seed and look, on
 * [dispatcher], and kept; meanwhile the enclosing colors show, and a change cross-fades as a theme change does. The
 * typography, shapes, category colors and chat style stay the enclosing theme's.
 */
@Composable
fun WallpaperColorsTheme(
    seed: Int?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    content: @Composable () -> Unit,
) {
    val settings = LocalThemeSettings.current
    val contrast = systemContrastOrNull() ?: settings.contrast
    val key = seed?.let { WallpaperSchemeKey(it, settings.dark, contrast, settings.amoledBlack) }
    val target by produceState(WallpaperSchemes.cached(key), key) {
        value = key?.let { withContext(dispatcher) { WallpaperSchemes.get(it) } }
    }
    val enclosing = MaterialTheme.colorScheme
    val enclosingColors = LocalPingogramColors.current
    // Without a scheme the enclosing one shows as it is, cross-fading with the app's theme; the transition still
    // follows it, so a scheme that arrives fades in from the colors on screen.
    val transition = animateColorScheme(target ?: enclosing, ExpressiveMotionScheme.defaultEffectsSpec())
    val colorScheme = if (target == null) enclosing else transition
    val colors =
        remember(colorScheme, enclosingColors, settings.dark) {
            if (colorScheme === enclosing) {
                enclosingColors
            } else {
                pingogramColors(colorScheme, settings.dark, enclosingColors.categories)
            }
        }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = ExpressiveMotionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
    ) {
        CompositionLocalProvider(LocalPingogramColors provides colors, content = content)
    }
}

/** A wallpaper scheme's seed and look: what [WallpaperSchemes] keeps one scheme for. */
internal data class WallpaperSchemeKey(
    val seed: Int,
    val dark: Boolean,
    val contrast: Contrast,
    val amoledBlack: Boolean,
)

/**
 * The wallpaper schemes made so far, the most recently used few kept: opening a chat again, or another with the same
 * wallpaper, needs no new scheme.
 */
internal object WallpaperSchemes {
    private const val KEPT = 8

    private val schemes =
        object : LinkedHashMap<WallpaperSchemeKey, ColorScheme>(KEPT, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WallpaperSchemeKey, ColorScheme>?): Boolean =
                size > KEPT
        }

    /** The scheme of [key] if it was made already; null for a null key or one not made yet. */
    fun cached(key: WallpaperSchemeKey?): ColorScheme? = key?.let { synchronized(schemes) { schemes[it] } }

    /** The scheme of [key], made now if it was not yet: a few milliseconds, so off the main thread. */
    fun get(key: WallpaperSchemeKey): ColorScheme =
        cached(key) ?: make(key).also { synchronized(schemes) { schemes[key] = it } }

    private fun make(key: WallpaperSchemeKey): ColorScheme {
        val scheme = contentColorScheme(Color(key.seed), key.dark, key.contrast)
        return if (key.amoledBlack) scheme.withAmoledSurfaces() else scheme
    }

    private const val LOAD_FACTOR = 0.75f
}
