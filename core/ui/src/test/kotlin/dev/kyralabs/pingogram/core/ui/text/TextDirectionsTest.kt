package dev.kyralabs.pingogram.core.ui.text

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** How [userContent] and [numericContent] set the direction of text in a right-to-left layout. */
@RunWith(RobolectricTestRunner::class)
class TextDirectionsTest {
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
    fun `user content takes the direction of its first strong character`() {
        val directions =
            directionsInRtl(
                "Thanks!" to TextStyle().userContent,
                "שלום!" to TextStyle().userContent,
                "12:45" to TextStyle().userContent,
            )

        assertEquals(listOf(Ltr, Rtl, Rtl), directions)
    }

    @Test
    fun `numeric content reads left to right without a strong character`() {
        val directions =
            directionsInRtl(
                "+44 7911 123456" to TextStyle().numericContent,
                "רחוב הרצל 1" to TextStyle().numericContent,
            )

        assertEquals(listOf(Ltr, Rtl), directions)
    }

    @Test
    fun `text without a direction keeps the layout's, as Pingogram's own strings do`() {
        assertEquals(listOf(Rtl), directionsInRtl("Thanks!" to TextStyle()))
    }

    /** The direction of the first paragraph of each text, laid out in a right-to-left layout in its style. */
    private fun directionsInRtl(vararg texts: Pair<String, TextStyle>): List<ResolvedTextDirection> {
        val directions = arrayOfNulls<ResolvedTextDirection>(texts.size)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column {
                    texts.forEachIndexed { index, (text, style) ->
                        Text(text, style = style, onTextLayout = { directions[index] = it.getParagraphDirection(0) })
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return directions.map { checkNotNull(it) }
    }

    private companion object {
        val Ltr = ResolvedTextDirection.Ltr
        val Rtl = ResolvedTextDirection.Rtl
    }
}
