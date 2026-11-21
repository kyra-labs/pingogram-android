package dev.kyralabs.pingogram.core.ui.theme

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** [WallpaperColorsTheme]: the enclosing colors without a seed, the seed's scheme with one, AMOLED black kept. */
@RunWith(RobolectricTestRunner::class)
class WallpaperColorsThemeTest {
    // Declares the activity to Robolectric, as ui-test-manifest would.
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var enclosing: ColorScheme
    private lateinit var shown: ColorScheme
    private lateinit var bubbles: PingogramColors

    private fun setContent(
        seed: () -> Int?,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled) {
                enclosing = MaterialTheme.colorScheme
                WallpaperColorsTheme(seed(), dispatcher = Dispatchers.Unconfined) {
                    shown = MaterialTheme.colorScheme
                    bubbles = LocalPingogramColors.current
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLED_MILLIS)
        composeRule.waitForIdle()
    }

    @Test
    fun `without a seed the conversation keeps the app's colors exactly`() {
        setContent(seed = { null })

        assertSame(enclosing, shown)
    }

    @Test
    fun `with a seed the conversation takes the seed's scheme, bubbles included`() {
        setContent(seed = { SEED })

        val expected = contentColorScheme(Color(SEED), dark = false, Contrast.Standard)
        assertEquals(expected.primary, shown.primary)
        assertEquals(expected.primaryContainer, bubbles.outgoingBubble.container)
    }

    @Test
    fun `AMOLED black stays black in a wallpaper's colors`() {
        setContent(seed = { SEED }, themeMode = ThemeMode.Dark, amoled = true)

        assertEquals(Color.Black, shown.surface)
    }

    @Test
    fun `a seed that arrives later replaces the app's colors`() {
        var seed by mutableStateOf<Int?>(null)
        setContent(seed = { seed })

        seed = SEED
        composeRule.mainClock.advanceTimeBy(SETTLED_MILLIS)
        composeRule.waitForIdle()

        assertEquals(contentColorScheme(Color(SEED), dark = false, Contrast.Standard).primary, shown.primary)
    }

    private companion object {
        const val SEED = 0xFFB4744B.toInt()
        const val SETTLED_MILLIS = 2_000L
    }
}
