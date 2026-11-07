package dev.kyralabs.pingogram.feature.media.ui

import androidx.core.view.WindowInsetsControllerCompat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [ImmersiveBars]: the viewer changes a window attribute only where it differs, and puts back only what it changed
 * (`docs/audit/performance-review-2026-11.md`, M3: each change is a window relayout in the viewer's first and last
 * frames).
 */
class ImmersiveBarsTest {
    @Test
    fun `in dark theme only the bars' behaviour changes, and only it is put back`() {
        val bars = RecordingBars(lightStatusBars = false, lightNavigationBars = false, behavior = DEFAULT)
        val immersive = ImmersiveBars(bars)

        immersive.enter()
        immersive.hide(false)
        immersive.leave()

        assertEquals(listOf("behavior=$SWIPE", "behavior=$DEFAULT"), bars.changes)
    }

    @Test
    fun `in light theme the icons turn light, and come back dark`() {
        val bars = RecordingBars(lightStatusBars = true, lightNavigationBars = true, behavior = SWIPE)
        val immersive = ImmersiveBars(bars)

        immersive.enter()
        immersive.leave()

        assertEquals(
            listOf("status=false", "navigation=false", "status=true", "navigation=true"),
            bars.changes,
        )
    }

    @Test
    fun `bars hide and show only when that changes, and show again on leaving only if hidden`() {
        val bars = RecordingBars(lightStatusBars = false, lightNavigationBars = false, behavior = SWIPE)
        val immersive = ImmersiveBars(bars)

        immersive.enter()
        immersive.hide(false)
        immersive.hide(true)
        immersive.hide(true)
        immersive.leave()

        assertEquals(listOf("hide", "show"), bars.changes)
    }

    private class RecordingBars(
        lightStatusBars: Boolean,
        lightNavigationBars: Boolean,
        behavior: Int,
    ) : SystemBars {
        val changes = mutableListOf<String>()

        override var lightStatusBars = lightStatusBars
            set(value) {
                field = value
                changes += "status=$value"
            }

        override var lightNavigationBars = lightNavigationBars
            set(value) {
                field = value
                changes += "navigation=$value"
            }

        override var behavior = behavior
            set(value) {
                field = value
                changes += "behavior=$value"
            }

        override fun hide() {
            changes += "hide"
        }

        override fun show() {
            changes += "show"
        }
    }

    private companion object {
        const val SWIPE = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        const val DEFAULT = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }
}
