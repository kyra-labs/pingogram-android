package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.kyralabs.pingogram.core.ui.file.ExternalFileResult
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * How the conversation's route handles a downloaded document to open ([ChatEffect.OpenFile]): handed to another app
 * with the document's type and the chooser's title, and a snackbar when no app takes it or the file is gone. The app's
 * `FileProvider` itself is tested in `:app`, which declares it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatEffectsTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val effects = Channel<ChatEffect>(Channel.BUFFERED)
    private val opened = mutableListOf<String>()

    private fun setEffects(result: ExternalFileResult) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val snackbarHostState = remember { SnackbarHostState() }
                ChatEffects(
                    effects = remember { effects.receiveAsFlow() },
                    snackbarHostState = snackbarHostState,
                    jumpState = rememberMessageJumpState(),
                    onOpenMedia = {},
                    openFile = { path, mimeType, chooserTitle ->
                        opened += "$path $mimeType $chooserTitle"
                        result
                    },
                )
                SnackbarHost(snackbarHostState)
            }
        }
    }

    @Test
    fun `a document opens in another app, as its type`() {
        setEffects(ExternalFileResult.Started)

        effects.trySend(ChatEffect.OpenFile("/files/documents/report.pdf", "application/pdf"))
        composeRule.waitForIdle()

        assertEquals(
            listOf("/files/documents/report.pdf application/pdf ${string(R.string.content_open_with)}"),
            opened,
        )
        composeRule.onNodeWithText(string(R.string.content_no_app_for_file)).assertDoesNotExist()
    }

    @Test
    fun `no app for a document is a snackbar`() {
        setEffects(ExternalFileResult.NoApp)

        effects.trySend(ChatEffect.OpenFile("/files/documents/report.xyz", null))
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.content_no_app_for_file)).assertExists()
    }

    @Test
    fun `a document that is gone is a snackbar`() {
        setEffects(ExternalFileResult.Unavailable)

        effects.trySend(ChatEffect.OpenFile("/files/documents/report.pdf", "application/pdf"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.content_file_unavailable)).assertExists()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}
