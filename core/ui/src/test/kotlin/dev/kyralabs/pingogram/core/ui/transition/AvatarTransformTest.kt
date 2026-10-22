package dev.kyralabs.pingogram.core.ui.transition

import android.animation.ValueAnimator
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.After
import org.junit.Assert.assertNotEquals
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
 * The container transform from a conversation's avatar into the profile or info page its title opens
 * ([avatarTransform], [avatarTransformTarget], `docs/DESIGN_SYSTEM.md` §5): shared only inside the `NavHost`'s scopes,
 * never for a title that opens nothing, and never with reduced motion; a profile and an info page never share one.
 */
@RunWith(RobolectricTestRunner::class)
class AvatarTransformTest {
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

    private val base = Modifier.size(40.dp)

    @Test
    fun `in the scopes the avatar shares its bounds`() {
        assertNotSame(base, base.transformed(inScopes = true) { avatarTransform(AvatarTransformKey.profile(USER)) })
    }

    @Test
    fun `in the scopes the page shares its bounds`() {
        assertNotSame(
            base,
            base.transformed(inScopes = true) { avatarTransformTarget(AvatarTransformKey.profile(USER)) },
        )
    }

    @Test
    fun `a title that opens nothing shares nothing`() {
        assertSame(base, base.transformed(inScopes = true) { avatarTransform(null) })
    }

    @Test
    fun `outside the scopes nothing is shared`() {
        assertSame(base, base.transformed(inScopes = false) { avatarTransform(AvatarTransformKey.profile(USER)) })
    }

    @Test
    fun `with reduced motion the avatar shares nothing`() {
        setAnimatorDurationScale(0f)
        check(!ValueAnimator.areAnimatorsEnabled())

        assertSame(base, base.transformed(inScopes = true) { avatarTransform(AvatarTransformKey.profile(USER)) })
    }

    @Test
    fun `with reduced motion the page shares nothing`() {
        setAnimatorDurationScale(0f)
        check(!ValueAnimator.areAnimatorsEnabled())

        assertSame(base, base.transformed(inScopes = true) { avatarTransformTarget(AvatarTransformKey.chatInfo(USER)) })
    }

    @Test
    fun `a profile and an info page of the same id are different keys`() {
        assertNotEquals(AvatarTransformKey.profile(USER), AvatarTransformKey.chatInfo(USER))
    }

    /** What [transform] makes of this modifier, in the scopes the app's `NavHost` provides or without them. */
    private fun Modifier.transformed(
        inScopes: Boolean,
        transform: @Composable Modifier.() -> Modifier,
    ): Modifier {
        val original = this
        lateinit var result: Modifier
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val content = @Composable {
                    result = original.transform()
                    Box(result)
                }
                if (inScopes) InScopes(content) else content()
            }
        }
        composeRule.waitForIdle()
        return result
    }

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

    private companion object {
        const val USER = 101L
    }
}
