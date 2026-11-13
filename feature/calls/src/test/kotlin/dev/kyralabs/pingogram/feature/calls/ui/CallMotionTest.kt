package dev.kyralabs.pingogram.feature.calls.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.SnapSpec
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.ui.component.ItemMotion
import dev.kyralabs.pingogram.core.ui.component.rememberItemMotion
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The call screens' motion ([callMotionScheme]): the theme's, or while power saving holds the call screens still,
 * one whose every spec snaps, which the call lists' item motion takes too.
 */
@RunWith(RobolectricTestRunner::class)
class CallMotionTest {
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

    @Test
    fun `while power saving holds the call screens still, every spec and the lists' item motion snap`() {
        val (scheme, items) = read(still = true)

        assertSame(StillMotionScheme, scheme)
        listOf(
            scheme.defaultSpatialSpec<Float>(),
            scheme.fastSpatialSpec(),
            scheme.slowSpatialSpec(),
            scheme.defaultEffectsSpec(),
            scheme.fastEffectsSpec(),
            scheme.slowEffectsSpec(),
            items.fadeIn,
            items.placement,
            items.fadeOut,
        ).forEach { assertTrue("$it", it is SnapSpec<*>) }
    }

    @Test
    fun `otherwise the call screens move on the theme's scheme`() {
        val (scheme, items) = read(still = false)

        assertNotSame(StillMotionScheme, scheme)
        assertTrue(items.fadeIn !is SnapSpec<*>)
    }

    private fun read(still: Boolean): Pair<MotionScheme, ItemMotion> {
        var scheme: MotionScheme? = null
        var items: ItemMotion? = null
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalStillCallAnimations provides still) {
                    scheme = callMotionScheme()
                    items = rememberItemMotion(callMotionScheme())
                }
            }
        }
        composeRule.waitForIdle()
        return checkNotNull(scheme) to checkNotNull(items)
    }
}
