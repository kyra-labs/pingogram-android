package dev.kyralabs.pingogram.feature.chat.ui

import android.os.Build
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
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerFocusState
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
 * `FileProvider` itself is tested in `:app`, which declares it. Also a copied text ([ChatEffect.CopyText]), which goes
 * to the clipboard with a snackbar only before Android 13, a focus request for the composer, and an action that is
 * not allowed.
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
    private val copied = mutableListOf<String>()
    private val composerFocus = ComposerFocusState()

    private fun setEffects(result: ExternalFileResult = ExternalFileResult.Started) {
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
                    copyText = { copied += it },
                    composerFocus = composerFocus,
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

    @Test
    fun `a copied text goes to the clipboard, which Android 13 and later confirm themselves`() {
        setEffects()

        effects.trySend(ChatEffect.CopyText("See you there!"))
        composeRule.waitForIdle()

        assertEquals(listOf("See you there!"), copied)
        composeRule.onNodeWithText(string(R.string.message_copied)).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun `before Android 13 a snackbar confirms the copy`() {
        setEffects()

        effects.trySend(ChatEffect.CopyText("See you there!"))
        composeRule.waitForIdle()

        assertEquals(listOf("See you there!"), copied)
        composeRule.onNodeWithText(string(R.string.message_copied)).assertExists()
    }

    @Test
    fun `the composer is asked for the focus`() {
        setEffects()

        effects.trySend(ChatEffect.FocusComposer)
        composeRule.waitForIdle()

        assertEquals(1, composerFocus.requests)
    }

    @Test
    fun `an action that is not allowed is a snackbar`() {
        setEffects()

        effects.trySend(ChatEffect.ActionUnavailable)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.message_action_unavailable)).assertExists()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}
