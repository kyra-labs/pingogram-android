package dev.kyralabs.pingogram.feature.media.profilemusic

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
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
 * Profile music has named controls with 48 dp touch targets ([assertAccessible], `docs/audit/accessibility.md`): the
 * cover opens the playlist, play and the next song are named, a song plays on a tap, and the user's own songs offer
 * moving and removing as custom actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProfileMusicAccessibilityChecksTest {
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
    fun card() {
        val events = mutableListOf<ProfileMusicEvent>()
        val progress = mutableFloatStateOf(0.3f)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ProfileMusicContent(ProfileMusicPreviewData.other, progress, { events += it }, {})
            }
        }

        composeRule.assertAccessible()
        composeRule.onNodeWithContentDescription("Open playlist").assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Next song").performClick()
        assertEquals(
            listOf(ProfileMusicEvent.OpenSheet, ProfileMusicEvent.TogglePlay, ProfileMusicEvent.Next),
            events,
        )
    }

    @Test
    fun ownEmptyCard() {
        var added = false
        val progress = mutableFloatStateOf(0f)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ProfileMusicContent(ProfileMusicPreviewData.ownEmpty, progress, {}, { added = true })
            }
        }

        composeRule.assertAccessible()
        composeRule.onNodeWithText("Add music").performClick()
        assertTrue(added)
    }

    @Test
    fun playlist() {
        val events = mutableListOf<ProfileMusicEvent>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ProfileMusicSheetContent(ProfileMusicPreviewData.own, false, { events += it }, {})
            }
        }

        composeRule.assertAccessible()
        composeRule.onNodeWithText("Monsoon Tape", useUnmergedTree = true).assertExists()
        val row = composeRule.onNodeWithContentDescription("Options for \u2068Monsoon Tape\u2069")
        row.assertHasClickAction()
        val actions =
            composeRule
                .onNodeWithText("Chai at 2 AM", useUnmergedTree = true)
                .fetchSemanticsNode()
                .let { node ->
                    generateSequence(node) { it.parent }.first { SemanticsActions.CustomActions in it.config }
                }.config[SemanticsActions.CustomActions]
                .map { it.label }
        assertEquals(listOf("Move up", "Move down", "Remove"), actions)
    }
}
