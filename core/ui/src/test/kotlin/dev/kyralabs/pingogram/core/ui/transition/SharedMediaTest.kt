package dev.kyralabs.pingogram.core.ui.transition

import android.animation.ValueAnimator
import android.content.ComponentName
import android.graphics.Region
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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
 * [sharedMediaBounds] and [sharedMediaViewerBounds]: nothing without the scopes the app's `NavHost` provides
 * (previews, a screen's own tests), nor with reduced motion or when disabled; with them, the container transform runs
 * between two contents of the same message and ends with the target shown, a bubble publishes its media's corners
 * ([SharedMediaShapes]), and the viewer's clip morphs from and back to exactly those.
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
                    shared = base.sharedMediaBounds(MESSAGE, MaterialTheme.shapes.large, enabled)
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
                                val shape = MaterialTheme.shapes.large
                                Box(
                                    Modifier
                                        .size(size)
                                        .then(
                                            if (viewer) {
                                                Modifier.sharedMediaViewerBounds(MESSAGE)
                                            } else {
                                                Modifier.sharedMediaBounds(MESSAGE, shape)
                                            },
                                        ).testTag(if (viewer) "viewer" else "bubble"),
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

    @Test
    fun `a bubble publishes its media's corners for the viewer while it is laid out`() {
        var shown by mutableStateOf(true)
        lateinit var shapes: SharedMediaShapes
        lateinit var bubbleShape: CornerBasedShape
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                bubbleShape = MaterialTheme.shapes.extraLarge.copy(bottomEnd = ZeroCornerSize)
                InScopes {
                    shapes = LocalSharedMediaShapes.current!!
                    if (shown) Box(Modifier.size(48.dp).sharedMediaBounds(MESSAGE, bubbleShape))
                    Box(Modifier.size(48.dp).sharedMediaViewerBounds(OTHER_MESSAGE))
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(bubbleShape, shapes[MESSAGE])
        // The viewer's side publishes nothing.
        assertNull(shapes[OTHER_MESSAGE])

        shown = false
        composeRule.waitForIdle()

        assertNull(shapes[MESSAGE])
    }

    @Test
    fun `a bubble that leaves keeps the shape another bubble of its message published since`() {
        var first by mutableStateOf(true)
        lateinit var shapes: SharedMediaShapes
        lateinit var secondShape: CornerBasedShape
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                secondShape = MaterialTheme.shapes.extraSmall
                InScopes {
                    shapes = LocalSharedMediaShapes.current!!
                    if (first) Box(Modifier.size(48.dp).sharedMediaBounds(MESSAGE, MaterialTheme.shapes.large))
                    Box(Modifier.size(48.dp).sharedMediaBounds(MESSAGE, secondShape))
                }
            }
        }
        composeRule.waitForIdle()

        first = false
        composeRule.waitForIdle()

        assertEquals(secondShape, shapes[MESSAGE])
    }

    @Test
    fun `the viewer's media starts from and ends on exactly the bubble's corners`() {
        val bubble = RoundedCornerShape(CORNER_PX)
        val shapes = SharedMediaShapes()
        lateinit var state: SharedTransitionScope.SharedContentState
        composeRule.setContent {
            SharedTransitionLayout { state = rememberSharedContentState(MESSAGE) }
        }
        composeRule.waitForIdle()
        val publish = PublishedMediaShapeNode(shapes, MESSAGE, bubble)
        shapes.publish(publish)
        var fraction = 0f
        val clip = MorphingClip(viewerCounterpart(shapes, MESSAGE), FullBleedShape) { fraction }
        val bounds = Rect(0f, 0f, SIZE_PX, SIZE_PX)

        fun cornerCovered(): Boolean {
            val path = clip.getClipPath(state, bounds, LayoutDirection.Ltr, Density(1f)).asAndroidPath()
            val region = Region().apply { setPath(path, Region(0, 0, SIZE_PX.toInt(), SIZE_PX.toInt())) }
            return region.contains(1, 1)
        }

        // In the bubble (as the viewer opens and once it has closed): the bubble's round corner.
        assertFalse(cornerCovered())
        fraction = 1f
        // Full screen: square.
        assertTrue(cornerCovered())

        shapes.withdraw(publish)
        fraction = 0f
        // With no bubble to go back to, nothing to morph from: square throughout.
        assertTrue(cornerCovered())
    }

    /**
     * [content] in a shared transition layout ([ProvideSharedTransitionScope], with its [SharedMediaShapes]) and a
     * destination's animated visibility scope, as in the app.
     */
    @Composable
    private fun InScopes(content: @Composable () -> Unit) {
        SharedTransitionLayout {
            ProvideSharedTransitionScope(this) {
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
        const val OTHER_MESSAGE = 43L
        const val SIZE_PX = 100f
        const val CORNER_PX = 40f
        const val TAG = "media"
        const val FRAMES = 10
    }
}
