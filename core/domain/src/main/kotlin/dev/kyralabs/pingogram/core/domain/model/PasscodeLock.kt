package dev.kyralabs.pingogram.core.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** What a passcode is made of: digits, typed on the lock screen's keypad, or any characters, typed on the keyboard. */
enum class PasscodeType {
    Pin,
    Password,
    ;

    /**
     * Whether [passcode] can be set as a passcode of this type: [PasscodeRules.MIN_LENGTH] characters at least, digits
     * only and [PasscodeRules.MAX_PIN_LENGTH] at most for a PIN, [PasscodeRules.MAX_PASSWORD_LENGTH] at most and not
     * only spaces for a password.
     */
    fun accepts(passcode: String): Boolean =
        passcode.length in PasscodeRules.MIN_LENGTH..maxLength &&
            when (this) {
                Pin -> passcode.all(Char::isAsciiDigit)
                Password -> passcode.isNotBlank()
            }

    /** The most characters a passcode of this type holds; input stops there. */
    val maxLength: Int
        get() =
            when (this) {
                Pin -> PasscodeRules.MAX_PIN_LENGTH
                Password -> PasscodeRules.MAX_PASSWORD_LENGTH
            }
}

/** The lengths a passcode may have (`REQUIREMENTS.md` FR-S1: a 4–8 digit PIN, or a password). */
object PasscodeRules {
    const val MIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 64
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

/** How long Pingogram may stay out of sight before it locks: from the moment it leaves the screen. */
enum class AutoLock(
    val delay: Duration,
) {
    Immediately(Duration.ZERO),
    OneMinute(1.minutes),
    FiveMinutes(5.minutes),
    OneHour(1.hours),
}

/**
 * The passcode lock (`REQUIREMENTS.md` FR-S1, §6.2), kept on this device. The passcode itself never leaves the
 * repository that stores it: only its salted hash is saved, and only that repository compares a passcode with it.
 *
 * @property isSet whether a passcode is set, which turns the lock on.
 * @property type whether the passcode is a PIN or a password, so the lock screen shows the keypad or the keyboard.
 * @property autoLock how long the app may stay out of sight before it locks.
 * @property biometricUnlock whether a fingerprint or the face, where the device has strong biometrics, unlocks too.
 * @property deviceCredentialUnlock whether the phone's own screen lock (its PIN, pattern or password) unlocks too, on
 *   Android 11 and later; off by default, as anyone who knows it gets past the passcode.
 * @property hideContent whether the app's content stays hidden in the list of recent apps.
 * @property failedAttempts wrong passcodes entered since the last right one, which make the next attempts wait.
 */
data class PasscodeSettings(
    val isSet: Boolean = false,
    val type: PasscodeType = PasscodeType.Pin,
    val autoLock: AutoLock = AutoLock.OneMinute,
    val biometricUnlock: Boolean = true,
    val deviceCredentialUnlock: Boolean = false,
    val hideContent: Boolean = true,
    val failedAttempts: Int = 0,
)
