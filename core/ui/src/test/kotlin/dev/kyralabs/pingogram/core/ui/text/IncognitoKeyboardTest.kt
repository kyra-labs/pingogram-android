package dev.kyralabs.pingogram.core.ui.text

import android.content.ComponentName
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [IncognitoKeyboard] (`REQUIREMENTS.md` FR-X7): a text field inside it tells the keyboard not to learn while it is on,
 * and tells it nothing new while it is off.
 */
@RunWith(RobolectricTestRunner::class)
class IncognitoKeyboardTest {
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

    @Test
    fun `on, a focused field asks the keyboard not to learn`() {
        assertEquals(NO_LEARNING, focusedFieldOptions(enabled = true) and NO_LEARNING)
    }

    @Test
    fun `off, a focused field asks nothing more of the keyboard`() {
        assertEquals(0, focusedFieldOptions(enabled = false) and NO_LEARNING)
    }

    /** The IME options a text field inside [IncognitoKeyboard] gives the keyboard once it is focused. */
    private fun focusedFieldOptions(enabled: Boolean): Int {
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            IncognitoKeyboard(enabled) {
                BasicTextField(rememberTextFieldState(), Modifier.testTag(FIELD))
            }
        }
        composeRule.onNodeWithTag(FIELD).performClick()
        composeRule.waitForIdle()
        val attributes = EditorInfo()
        composeRule.runOnUiThread { assertNotNull("no input connection", view.onCreateInputConnection(attributes)) }
        return attributes.imeOptions
    }

    private companion object {
        const val FIELD = "field"
        const val NO_LEARNING = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }
}
