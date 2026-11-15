package dev.kyralabs.pingogram.feature.chat.ui.transcription

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.provider.Settings
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.TranscriptionEffect
import dev.kyralabs.pingogram.feature.chat.ui.TranscriptionEvent

/** The text screen readers hear of [transcript], in the bubble's description. */
internal fun transcriptDescription(
    resources: Resources,
    transcript: BubbleTranscript,
): String =
    when (transcript) {
        BubbleTranscript.Transcribing -> {
            resources.getString(R.string.transcription_transcribing)
        }

        is BubbleTranscript.Shown -> {
            resources.getString(
                R.string.transcription_a11y_text,
                transcript.text.ifEmpty { resources.getString(R.string.transcription_no_words) },
            )
        }

        BubbleTranscript.LanguageUnavailable -> {
            resources.getString(R.string.transcription_unavailable)
        }

        BubbleTranscript.Failed -> {
            resources.getString(R.string.transcription_failed)
        }
    }

/**
 * The custom actions of a voice or round video [bubble] for screen readers, where the device can transcribe
 * ([canTranscribe]): "Transcribe voice message" or "Hide text", and "Android language settings" or "Try again" where
 * the transcript failed; as label and event pairs.
 */
internal fun transcriptActions(
    resources: Resources,
    bubble: ChatItem.Bubble,
    canTranscribe: Boolean,
): List<Pair<String, ChatEvent>> {
    val isVideo = bubble.content is MessageContent.VideoNote
    if (!canTranscribe || !(isVideo || bubble.content is MessageContent.VoiceNote)) return emptyList()
    val transcript = bubble.transcript
    val toggle =
        if (transcript.isOpen || transcript == BubbleTranscript.Transcribing) {
            resources.getString(R.string.transcription_hide) to TranscriptionEvent.Hide(bubble.id)
        } else {
            val label = if (isVideo) R.string.transcription_transcribe_video else R.string.transcription_transcribe
            resources.getString(label) to TranscriptionEvent.Transcribe(bubble.id)
        }
    val more =
        when (transcript) {
            BubbleTranscript.LanguageUnavailable -> {
                resources.getString(R.string.transcription_language_settings) to TranscriptionEvent.OpenLanguageSettings
            }

            BubbleTranscript.Failed -> {
                resources.getString(R.string.transcription_try_again) to TranscriptionEvent.Transcribe(bubble.id)
            }

            else -> {
                null
            }
        }
    return listOfNotNull(toggle, more)
}

/**
 * The snackbar of [effect]: why the microphone permission is asked for, or that it was refused; Android's speech
 * language settings opened (or, where none, that the phone has no such page).
 */
internal fun Context.transcriptionNotice(
    effect: TranscriptionEffect,
    resources: Resources,
): String =
    when (effect) {
        TranscriptionEffect.ExplainMicrophone -> {
            resources.getString(R.string.transcription_permission_rationale)
        }

        TranscriptionEffect.MicrophoneDenied -> {
            resources.getString(R.string.transcription_permission_denied)
        }

        TranscriptionEffect.OpenLanguageSettings -> {
            if (openSpeechSettings()) "" else resources.getString(R.string.transcription_no_settings)
        }
    }

/** Android's page of speech languages (its voice input settings), else its language settings; whether one opened. */
private fun Context.openSpeechSettings(): Boolean =
    listOf(Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_LOCALE_SETTINGS).any { action ->
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
