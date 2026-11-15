package dev.kyralabs.pingogram.feature.settings.ui.transcription

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The symbol of a speech language's row: ready, downloading, or not on the device. */
internal fun LanguageState.icon(): Int =
    when (this) {
        LanguageState.Ready -> UiR.drawable.ic_check_circle
        LanguageState.Downloading -> UiR.drawable.ic_download
        LanguageState.NotOnDevice -> R.drawable.ic_block
    }

@StringRes
internal fun LanguageState.label(): Int =
    when (this) {
        LanguageState.Ready -> R.string.settings_transcription_language_ready
        LanguageState.Downloading -> R.string.settings_transcription_language_downloading
        LanguageState.NotOnDevice -> R.string.settings_transcription_language_not_on_device
    }

/** Android's page of speech languages (its voice input settings), else its language settings; whether one opened. */
internal fun Context.openSpeechSettings(): Boolean =
    listOf(Settings.ACTION_VOICE_INPUT_SETTINGS, Settings.ACTION_LOCALE_SETTINGS).any { action ->
        try {
            startActivity(Intent(action))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
