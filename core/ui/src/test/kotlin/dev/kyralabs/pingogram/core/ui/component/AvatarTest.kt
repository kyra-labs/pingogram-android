package dev.kyralabs.pingogram.core.ui.component

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** [Avatar]'s semantics: decorative inside rows, which merge their own, or an image with a description. */
@RunWith(RobolectricTestRunner::class)
class AvatarTest {
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
    fun `adds nothing to a row that merges its semantics, initials included`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Row(Modifier.semantics(mergeDescendants = true) {}) {
                    Avatar(photo = null, name = NAME, id = 1, size = 56.dp)
                    Text(NAME)
                }
            }
        }

        composeRule
            .onNodeWithText(NAME)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString(NAME))))
        composeRule.onNodeWithText(INITIALS).assertDoesNotExist()
    }

    @Test
    fun `with a content description it is an image of that description`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Avatar(photo = null, name = NAME, id = 1, size = 56.dp, contentDescription = NAME)
            }
        }

        composeRule
            .onNodeWithContentDescription(NAME)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
        composeRule.onNodeWithText(INITIALS).assertDoesNotExist()
    }

    private companion object {
        const val NAME = "Ada Lovelace"
        const val INITIALS = "AL"
    }
}
