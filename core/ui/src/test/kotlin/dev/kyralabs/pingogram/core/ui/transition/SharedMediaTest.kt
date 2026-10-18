package dev.kyralabs.pingogram.core.ui.transition

import android.animation.ValueAnimator
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [sharedMediaBounds]: nothing without the scopes the app's `NavHost` provides (previews, a screen's own tests), nor
 * with reduced motion or when disabled; with them, the container transform runs between two contents of the same
 * message and ends with the target shown.
 */
@RunWith(RobolectricTestRunner::class)
class SharedMediaTest {
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

    @After
    fun restoreAnimations() {
        setAnimatorDurationScale(1f)
    }

    /** What [sharedMediaBounds] makes of this modifier in [content]'s scopes. */
    private fun Modifier.sharedIn(
        enabled: Boolean = true,
        content: @Composable (@Composable () -> Unit) -> Unit = { it() },
    ): Modifier {
        val base = this
        lateinit var shared: Modifier
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                content {
                    shared = base.sharedMediaBounds(MESSAGE, MaterialTheme.shapes.large, FullBleedShape, enabled)
                    Box(shared.testTag(TAG))
                }
            }
        }
        composeRule.waitForIdle()
        return shared
    }

    @Test
    fun `without the scopes nothing is shared, and the content shows`() {
        val base = Modifier.size(48.dp)

        assertSame(base, base.sharedIn())
        composeRule.onNodeWithTag(TAG).assertIsDisplayed()
    }

    @Test
    fun `without a destination's scope nothing is shared`() {
        val base = Modifier.size(48.dp)

        val shared =
            base.sharedIn { content ->
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) { content() }
                }
            }

        assertSame(base, shared)
    }

    @Test
    fun `in both scopes the bounds are shared`() {
        val base = Modifier.size(48.dp)

        assertNotSame(base, base.sharedIn { content -> InScopes(content) })
    }

    @Test
    fun `disabled, nothing is shared`() {
        val base = Modifier.size(48.dp)

        assertSame(base, base.sharedIn(enabled = false) { content -> InScopes(content) })
    }

    @Test
    fun `with reduced motion nothing is shared`() {
        setAnimatorDurationScale(0f)
        check(!ValueAnimator.areAnimatorsEnabled())
        val base = Modifier.size(48.dp)

        assertSame(base, base.sharedIn { content -> InScopes(content) })
    }

    @Test
    fun `the transform between two contents of one message ends with the target shown`() {
        var showViewer by mutableStateOf(false)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        AnimatedContent(targetState = showViewer, label = "destinations") { viewer ->
                            ProvideNavAnimatedVisibilityScope(this) {
                                val size = if (viewer) 200.dp else 48.dp
                                val shape = if (viewer) FullBleedShape else MaterialTheme.shapes.large
                                val counterpart = if (viewer) MaterialTheme.shapes.large else FullBleedShape
                                Box(
                                    Modifier
                                        .size(size)
                                        .sharedMediaBounds(MESSAGE, shape, counterpart)
                                        .testTag(if (viewer) "viewer" else "bubble"),
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("bubble").assertIsDisplayed()

        composeRule.mainClock.autoAdvance = false
        showViewer = true
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("viewer").assertIsDisplayed()
        composeRule.onNodeWithTag("bubble").assertDoesNotExist()

        showViewer = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("bubble").assertIsDisplayed()
        composeRule.onNodeWithTag("viewer").assertDoesNotExist()
    }

    /** [content] in a shared transition layout and a destination's animated visibility scope, as in the app. */
    @Composable
    private fun InScopes(content: @Composable () -> Unit) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                AnimatedVisibility(visible = true, label = "destination") {
                    ProvideNavAnimatedVisibilityScope(this) { content() }
                }
            }
        }
    }

    /**
     * Sets the animator duration scale as the system's developer setting does: 0 turns animations off. The platform's
     * setter is hidden from the SDK, so it is called by reflection.
     */
    private fun setAnimatorDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    private companion object {
        const val MESSAGE = 42L
        const val TAG = "media"
        const val FRAMES = 10
    }
}
