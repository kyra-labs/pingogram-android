package dev.kyralabs.pingogram.core.ui.phone

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated

// What a login code step says (`REQUIREMENTS.md` FR-A3), at sign-in and when changing the number in Settings.

/**
 * Where Telegram sent a code of [type] for [phoneNumber] (already isolated, `bidiIsolated`), as one sentence: "We sent
 * an SMS with the code to …".
 */
@Composable
fun loginCodeDescription(
    type: AuthCodeType,
    phoneNumber: String,
): String =
    when (type) {
        is AuthCodeType.TelegramMessage -> {
            stringResource(R.string.login_code_sent_telegram)
        }

        is AuthCodeType.Sms -> {
            stringResource(R.string.login_code_sent_sms, phoneNumber)
        }

        is AuthCodeType.SmsWord -> {
            withStart(
                type.firstLetter,
                R.string.login_code_sent_sms_word,
                R.string.login_code_sent_sms_word_start,
                phoneNumber,
            )
        }

        is AuthCodeType.SmsPhrase -> {
            withStart(
                type.firstWord,
                R.string.login_code_sent_sms_phrase,
                R.string.login_code_sent_sms_phrase_start,
                phoneNumber,
            )
        }

        is AuthCodeType.Call -> {
            stringResource(R.string.login_code_sent_call, phoneNumber)
        }

        is AuthCodeType.FlashCall -> {
            stringResource(R.string.login_code_sent_flash_call, phoneNumber)
        }

        is AuthCodeType.MissedCall -> {
            pluralStringResource(
                R.plurals.login_code_sent_missed_call,
                type.length,
                phoneNumber,
                type.phoneNumberPrefix.bidiIsolated(),
                type.length,
            )
        }

        is AuthCodeType.Fragment -> {
            stringResource(R.string.login_code_sent_fragment, phoneNumber)
        }

        is AuthCodeType.Firebase -> {
            stringResource(R.string.login_code_sent_unsupported)
        }
    }

/** [withoutStart] for [phoneNumber], or [withStart] naming the code's [start] too where Telegram tells it. */
@Composable
private fun withStart(
    start: String,
    @StringRes withoutStart: Int,
    @StringRes withStart: Int,
    phoneNumber: String,
): String =
    if (start.isBlank()) {
        stringResource(withoutStart, phoneNumber)
    } else {
        stringResource(withStart, phoneNumber, start.bidiIsolated())
    }

/** The button that asks for the code again by [type], the next way Telegram offers. */
@Composable
fun loginCodeResendText(type: AuthCodeType): String =
    when (type) {
        is AuthCodeType.Sms, is AuthCodeType.SmsWord, is AuthCodeType.SmsPhrase, is AuthCodeType.Firebase -> {
            stringResource(R.string.login_code_resend_sms)
        }

        is AuthCodeType.Call, is AuthCodeType.FlashCall, is AuthCodeType.MissedCall -> {
            stringResource(R.string.login_code_resend_call)
        }

        is AuthCodeType.TelegramMessage -> {
            stringResource(R.string.login_code_resend_telegram)
        }

        is AuthCodeType.Fragment -> {
            stringResource(R.string.login_code_resend_fragment)
        }
    }
