package dev.kyralabs.pingogram.feature.media.stickermaker

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Making a sticker has named controls with 48 dp touch targets ([assertAccessible], `docs/audit/accessibility.md`):
 * the tools, the photo's zoom and each layer's removal as custom actions, and the sheet's emoji and sets as radio
 * buttons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MakeStickerAccessibilityChecksTest {
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
    fun editor() {
        val events = mutableListOf<MakeStickerEvent>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                MakeStickerScreen(
                    state = MakeStickerPreviewData.editing,
                    photo = previewStickerPhoto(),
                    capture = null,
                    onEvent = { events += it },
                    onClose = {},
                )
            }
        }

        composeRule.assertAccessible()
        composeRule.onNodeWithText("Add text").assertHasClickAction().performClick()
        composeRule.onNodeWithText("Add emoji").performClick()
        composeRule.onNodeWithText("Next").assertIsEnabled().performClick()
        val emoji = composeRule.onNodeWithContentDescription("Emoji: 😴").fetchSemanticsNode()
        val remove = emoji.config[SemanticsActions.CustomActions].single { it.label == "Remove" }
        remove.action()
        val photo = composeRule.onNodeWithContentDescription("Sticker photo. Drag to move it, pinch to zoom.")
        val zoomIn =
            photo.fetchSemanticsNode().config[SemanticsActions.CustomActions].single {
                it.label == "Zoom in"
            }
        zoomIn.action()

        assertEquals(MakeStickerEvent.AddText, events[0])
        assertEquals(MakeStickerEvent.AddEmoji, events[1])
        assertEquals(MakeStickerEvent.Next, events[2])
        assertEquals(MakeStickerEvent.Remove(1), events[3])
        assertTrue((events[4] as MakeStickerEvent.Cropped).crop.zoom > MakeStickerPreviewData.editing.crop.zoom)
    }

    @Test
    fun sheet() {
        val events = mutableListOf<AddToSetEvent>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                AddToSetContent(MakeStickerPreviewData.choose, onEvent = { events += it })
            }
        }

        composeRule.assertAccessible()
        composeRule.onNodeWithText("😴").assertIsSelected()
        composeRule.onNodeWithText("Daily moods").performClick()
        composeRule.onNodeWithContentDescription("More emoji").performClick()
        composeRule.onNodeWithText("Add to Office cats").performClick()

        assertEquals(
            listOf(AddToSetEvent.Choose("dailymoods"), AddToSetEvent.MoreEmoji, AddToSetEvent.Confirm),
            events,
        )
    }
}
