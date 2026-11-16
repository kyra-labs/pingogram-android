package dev.kyralabs.pingogram.feature.contacts.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.contacts.ui.preview.NewContactPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Add contact's button keeps still while Telegram answers (`docs/plans/motion-expressive-plan.md` C-4, M-099): the
 * progress line's place is kept, so "Add" stays where the finger pressed it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class NewContactMotionTest {
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

    private var working by mutableStateOf(false)

    @Test
    fun `the add button stays where it is before, during and after a request`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                NewContactScreen(
                    state = NewContactPreviewData.phoneNumber.copy(isWorking = working),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        val before = addBounds()

        working = true
        composeRule.waitForIdle()
        val during = addBounds()

        working = false
        composeRule.waitForIdle()
        val after = addBounds()

        assertEquals(before, during)
        assertEquals(before, after)
    }

    private fun addBounds(): DpRect = composeRule.onNodeWithText(ADD).getUnclippedBoundsInRoot()

    private companion object {
        const val ADD = "Add"
    }
}
