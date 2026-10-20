package dev.kyralabs.pingogram.feature.chat.ui

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * How the conversation's route handles a downloaded document to open ([ChatEffect.OpenFile]): handed to another app
 * with the document's type and the chooser's title, and a snackbar when no app takes it or the file is gone. The app's
 * `FileProvider` itself is tested in `:app`, which declares it. Also a copied text ([ChatEffect.CopyText]), which goes
 * to the clipboard with a snackbar only before Android 13, a focus request for the composer, an action that is
 * not allowed, a sponsored message's link and what came of reporting one, a failed send's haptic; and the voice
 * recording's snackbars, whose actions ask for the microphone permission or open the settings.
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
    private val openedUris = mutableListOf<String>()
    private val performedHaptics = mutableListOf<HapticFeedbackType>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performedHaptics += hapticFeedbackType
            }
        }

    private fun setEffects(
        result: ExternalFileResult = ExternalFileResult.Started,
        uriOpens: Boolean = true,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val snackbarHostState = remember { SnackbarHostState() }
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    ChatEffects(
                        effects = remember { effects.receiveAsFlow() },
                        snackbarHostState = snackbarHostState,
                        onOpenMedia = {},
                        openFile = { path, mimeType, chooserTitle ->
                            opened += "$path $mimeType $chooserTitle"
                            result
                        },
                        copyText = { copied += it },
                        composerFocus = composerFocus,
                        openUri = { uri ->
                            openedUris += uri
                            uriOpens
                        },
                    )
                }
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
    fun `a send that failed is a reject haptic, without a snackbar`() {
        setEffects()

        effects.trySend(ChatEffect.SendFailed)
        composeRule.waitForIdle()

        // Robolectric runs Android 14, where the haptic is.
        assertEquals(listOf(HapticFeedbackType.Reject), performedHaptics)
        composeRule.onNodeWithText(string(R.string.chat_not_sent)).assertDoesNotExist()
    }

    @Test
    fun `an action that is not allowed is a snackbar`() {
        setEffects()

        effects.trySend(ChatEffect.ActionUnavailable)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.message_action_unavailable)).assertExists()
    }

    @Test
    fun `a sponsored message's link opens with the platform`() {
        setEffects()

        effects.trySend(ChatEffect.OpenUrl("https://t.me/sponsor"))
        composeRule.waitForIdle()

        assertEquals(listOf("https://t.me/sponsor"), openedUris)
        composeRule.onNodeWithText(string(R.string.sponsored_no_app)).assertDoesNotExist()
    }

    @Test
    fun `no app for a sponsored message's link is a snackbar`() {
        setEffects(uriOpens = false)

        effects.trySend(ChatEffect.OpenUrl("tg://resolve?domain=sponsor"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.sponsored_no_app)).assertExists()
    }

    @Test
    fun `what came of a report is a snackbar`() {
        setEffects()
        val notices =
            mapOf(
                SponsoredReportNotice.Reported to R.string.sponsored_reported,
                SponsoredReportNotice.AdsHidden to R.string.sponsored_ads_hidden,
                SponsoredReportNotice.PremiumRequired to R.string.sponsored_premium_required,
                SponsoredReportNotice.ReportFailed to R.string.sponsored_report_failed,
            )

        for ((notice, text) in notices) {
            effects.trySend(ChatEffect.SponsoredNotice(notice))
            composeRule.waitForIdle()

            composeRule.onNodeWithText(string(text)).assertExists()
        }
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `the microphone's reason is a snackbar whose action asks for the permission`() {
        setEffects()

        effects.trySend(VoiceEffect.ExplainMicrophone)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.voice_permission_rationale)).assertExists()
        composeRule.onNodeWithText(string(R.string.voice_permission_allow)).performClick()
        composeRule.waitForIdle()

        val request = shadowOf(composeRule.activity).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO), request?.requestedPermissions?.toList())
    }

    @Test
    fun `a refused microphone is a snackbar whose action opens the settings`() {
        setEffects()

        effects.trySend(VoiceEffect.MicrophoneDenied)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.voice_permission_denied)).assertExists()
        composeRule.onNodeWithText(string(R.string.voice_permission_settings)).performClick()
        composeRule.waitForIdle()

        val started = shadowOf(composeRule.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started?.action)
    }

    @Test
    fun `a recording too short to send says to hold, without an action`() {
        setEffects()

        effects.trySend(VoiceEffect.HoldToRecord)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.voice_hold_to_record)).assertExists()
        composeRule.onNodeWithText(string(R.string.voice_permission_allow)).assertDoesNotExist()
    }
}
