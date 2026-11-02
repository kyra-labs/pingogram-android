package dev.kyralabs.pingogram.feature.auth.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Welcome's links: Telegram's Terms of Service and Privacy Policy, which signing in accepts, and Pingogram's own
 * privacy policy, each a button of its own with its name and a 48 dp target, each opening its page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class WelcomeLinksTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AuthEvent>()

    @Test
    fun `each link is a button of its own that opens its page`() {
        composeRule.setContent {
            ScreenshotTheme(ScreenshotVariant.Light) {
                AuthScreen(state = AuthPreviewData.all.getValue("welcome"), onEvent = { events += it })
            }
        }

        listOf("Terms of Service", "Privacy Policy", "Pingogram's privacy policy").forEach { name ->
            val link = composeRule.onNodeWithText(name).assertHasClickAction()
            // The touch target, which Compose widens to 48 dp around the 40 dp button, as assertAccessible reads it.
            val target =
                with(composeRule.density) {
                    link
                        .fetchSemanticsNode()
                        .touchBoundsInRoot.size
                        .toDpSize()
                }
            assertTrue("$name: $target", target.width >= MIN_TARGET && target.height >= MIN_TARGET)
            link.performClick()
        }

        assertEquals(
            listOf(
                AuthEvent.OpenTermsOfService,
                AuthEvent.OpenTelegramPrivacyPolicy,
                AuthEvent.OpenPingogramPrivacyPolicy,
            ),
            events,
        )
    }

    private companion object {
        val MIN_TARGET = 48.dp
    }
}
