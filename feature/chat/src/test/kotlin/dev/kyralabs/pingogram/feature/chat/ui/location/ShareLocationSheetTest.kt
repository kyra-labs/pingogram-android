package dev.kyralabs.pingogram.feature.chat.ui.location

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachInputs
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollEditor
import dev.kyralabs.pingogram.feature.chat.ui.attach.rememberAttachInputs
import dev.kyralabs.pingogram.feature.chat.ui.attach.rememberAttachLaunchers
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.LocationPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Location" sheet over the conversation (`REQUIREMENTS.md` FR-C5): the attach menu's option opens it, its buttons
 * send their events, its location goes to the conversation, and screen readers hear the location as one. The screen is
 * stateless: these check what it sends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ShareLocationSheetTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val chatEvents = mutableListOf<ChatEvent>()
    private val events = mutableListOf<ShareLocationEvent>()

    private fun setSheet(
        state: ShareLocationUiState,
        canOpenMaps: Boolean = true,
        chat: ChatUiState = ChatPreviewData.privateChat,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(
                    state = chat,
                    onEvent = { chatEvents += it },
                    onBack = {},
                    attach = AttachInputs(location = LocationInputs({ state }, { events += it }, canOpenMaps)),
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the attach menu's location goes to the sheet's view model`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val inputs =
                    rememberAttachInputs(
                        launchers = rememberAttachLaunchers {},
                        onEvent = {},
                        caption = ComposerText(),
                        pollEditor = PollEditor(),
                        snackbarHostState = SnackbarHostState(),
                        location = LocationInputs(onEvent = { events += it }),
                    )
                ChatScreen(state = ChatPreviewData.privateChat, onEvent = {}, onBack = {}, attach = inputs)
            }
        }
        composeRule.onNodeWithContentDescription("Attach").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Location").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(ShareLocationEvent.Open), events)
    }

    @Test
    fun `the explanation goes on to Android's question, or away`() {
        setSheet(ShareLocationUiState.Permission())

        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(listOf(ShareLocationEvent.Allow, ShareLocationEvent.Dismiss), events)
    }

    @Test
    fun `refused for good, the button opens the settings`() {
        setSheet(ShareLocationUiState.Permission(deniedForGood = true))

        composeRule.onNodeWithText("Open settings").performClick()

        assertEquals(listOf(ShareLocationEvent.Allow), events)
    }

    @Test
    fun `sending hides the sheet, then the location goes to the conversation and the sheet closes`() {
        setSheet(LocationPreviewData.found)

        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.waitForIdle()

        val location = checkNotNull(LocationPreviewData.found.location)
        assertEquals(
            listOf(ChatEvent.SendLocation(location, SendOptions())),
            chatEvents.filterIsInstance<ChatEvent.SendLocation>(),
        )
        assertEquals(listOf(ShareLocationEvent.Dismiss), events)
    }

    @Test
    fun `screen readers hear the coordinates and their accuracy as one`() {
        setSheet(LocationPreviewData.found)

        composeRule
            .onNodeWithContentDescription(
                "51.49781 degrees north, 0.17453 degrees west. Accurate to 8 m",
            ).assertIsDisplayed()
    }

    @Test
    fun `open in maps shows only where an app can`() {
        setSheet(LocationPreviewData.found)
        composeRule.onNodeWithText("Open in maps").performClick()
        assertEquals(listOf(ShareLocationEvent.OpenInMaps), events)
    }

    @Test
    fun `without a maps app there is no open in maps`() {
        setSheet(LocationPreviewData.found, canOpenMaps = false)

        composeRule.onNodeWithText("Open in maps").assertDoesNotExist()
    }

    @Test
    fun `an approximate location offers the precise one`() {
        setSheet(LocationPreviewData.approximate)

        composeRule.onNodeWithText("Approximate location").assertIsDisplayed()
        composeRule.onNodeWithText("Use precise location").performClick()

        assertEquals(listOf(ShareLocationEvent.AskPrecise), events)
    }

    @Test
    fun `not found, it tries again`() {
        setSheet(ShareLocationUiState.NotFound)

        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(listOf(ShareLocationEvent.Retry), events)
    }

    @Test
    fun `no sheet while a message is edited, which sends no attachments`() {
        setSheet(LocationPreviewData.found, chat = ChatPreviewData.editing)

        composeRule.onNodeWithText("Send location").assertDoesNotExist()
    }
}
