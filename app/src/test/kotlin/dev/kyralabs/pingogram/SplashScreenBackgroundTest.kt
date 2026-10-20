package dev.kyralabs.pingogram

import android.app.Application
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.annotation.StyleRes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import androidx.core.splashscreen.R as SplashScreenR

/** The splash screen themes resolve to the backgrounds of the brand schemes, black for AMOLED dark (`themes.xml`). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SplashScreenBackgroundTest {
    @Test
    fun `brand colors follow the light system theme`() {
        assertEquals(BRAND_LIGHT, backgroundOf(R.style.Theme_Pingogram_Starting_SystemBrand))
        assertEquals(BRAND_LIGHT, backgroundOf(R.style.Theme_Pingogram_Starting_SystemBrandAmoled))
    }

    @Test
    @Config(qualifiers = "night")
    fun `brand colors follow the dark system theme, black with AMOLED`() {
        assertEquals(BRAND_DARK, backgroundOf(R.style.Theme_Pingogram_Starting_SystemBrand))
        assertEquals(Color.BLACK, backgroundOf(R.style.Theme_Pingogram_Starting_SystemBrandAmoled))
    }

    @Test
    @Config(qualifiers = "night")
    fun `a chosen theme mode ignores the system's`() {
        assertEquals(BRAND_LIGHT, backgroundOf(R.style.Theme_Pingogram_Starting_Light_Brand))
        assertEquals(Color.BLACK, backgroundOf(R.style.Theme_Pingogram_Starting_Dark_Black))
    }

    private fun backgroundOf(
        @StyleRes theme: Int,
    ): Int {
        val attributes =
            ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme)
                .obtainStyledAttributes(intArrayOf(SplashScreenR.attr.windowSplashScreenBackground))
        return try {
            attributes.getColor(0, Color.TRANSPARENT)
        } finally {
            attributes.recycle()
        }
    }

    private companion object {
        // The brand schemes' backgrounds (core/ui Color.kt).
        val BRAND_LIGHT = Color.parseColor("#FAF8FF")
        val BRAND_DARK = Color.parseColor("#10131C")
    }
}
