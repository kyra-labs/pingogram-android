package dev.kyralabs.pingogram.feature.settings.ui.passcode

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.PasscodeCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// What every passcode entry shares (the lock screen, checking the passcode before its settings, setting a new one): the
// text entered, what the entry says about it, and the wait after too many wrong attempts.

/**
 * What a passcode entry says: its [title], its [hint] while nothing went wrong, and the label of its [submit] action.
 */
@Immutable
internal data class PasscodeEntryTexts(
    val title: String,
    val hint: String,
    val submit: String,
)

/**
 * What a passcode entry shows besides the passcode itself, which is [PasscodeInput]'s.
 *
 * @property type the keypad for a PIN, the keyboard for a password.
 * @property message what went wrong with the last passcode entered; null shows the entry's hint.
 * @property rejections how many entries were rejected so far: each new one shakes the entry and buzzes.
 * @property isChecking whether an entered passcode is being checked or saved, which takes a moment (a slow hash).
 */
@Immutable
data class PasscodeEntryUiState(
    val type: PasscodeType = PasscodeType.Pin,
    val message: PasscodeMessage? = null,
    val rejections: Int = 0,
    val isChecking: Boolean = false,
) {
    /** Whether a passcode can be entered and submitted now: not while one is checked or attempts must wait. */
    val canSubmit: Boolean get() = !isChecking && message !is PasscodeMessage.Wait
}

/** What went wrong with the last passcode entered, shown in place of the entry's hint. */
@Immutable
sealed interface PasscodeMessage {
    data object Wrong : PasscodeMessage

    /** Too many wrong attempts: the next may follow in [seconds] (counted down). */
    data class Wait(
        val seconds: Long,
    ) : PasscodeMessage

    /** Shorter, or longer, than the passcode's type allows (setting one). */
    data object Invalid : PasscodeMessage

    /** The second entry differs from the first (setting one). */
    data object Mismatch : PasscodeMessage

    /** It could not be checked or saved, because of [error]. */
    data class Failed(
        val error: DomainError,
    ) : PasscodeMessage
}

/**
 * The passcode being entered, held by a view model for its entry: typed on the keypad ([append], [delete]) or on the
 * keyboard into [state]. Never saved anywhere: after process death, the entry starts empty.
 */
@Stable
class PasscodeInput {
    val state = TextFieldState()

    val text: String get() = state.text.toString()

    /** Adds [digit] at the end, unless the passcode reached the longest of [type]. */
    fun append(
        digit: Char,
        type: PasscodeType,
    ) {
        if (state.text.length < type.maxLength) state.edit { append(digit) }
    }

    /** Removes the last character. */
    fun delete() {
        if (state.text.isNotEmpty()) state.edit { delete(length - 1, length) }
    }

    fun clear() {
        state.clearText()
    }
}

/**
 * Checks passcodes entered into [input] with [check], counting the wait after too many wrong attempts down from
 * [remainingWait] in [scope] (a view model's). [entry] is what the entry shows; [onCorrect] runs for a right passcode.
 */
internal class PasscodeChecker(
    private val scope: CoroutineScope,
    private val check: suspend (String) -> PasscodeCheck,
    private val remainingWait: () -> Duration,
    private val onCorrect: () -> Unit,
) {
    val input = PasscodeInput()

    private val mutableEntry = MutableStateFlow(PasscodeEntryUiState())
    val entry: StateFlow<PasscodeEntryUiState> = mutableEntry.asStateFlow()

    private var countdown: Job? = null

    /** Shows the entry for [type], keeping what it says, and starts counting down a wait still running. */
    fun setType(type: PasscodeType) {
        mutableEntry.update { it.copy(type = type) }
        countDown()
    }

    /** Starts over: empty, with nothing said, but still counting down a wait. */
    fun reset() {
        input.clear()
        mutableEntry.update { it.copy(message = null, isChecking = false) }
        countDown()
    }

    fun submit() {
        val passcode = input.text
        if (passcode.isEmpty() || !entry.value.canSubmit) return
        mutableEntry.update { it.copy(isChecking = true) }
        scope.launch {
            val result = check(passcode)
            input.clear()
            when (result) {
                PasscodeCheck.Correct -> {
                    mutableEntry.update { it.copy(isChecking = false, message = null) }
                    onCorrect()
                }

                PasscodeCheck.Wrong -> {
                    reject(PasscodeMessage.Wrong)
                }

                is PasscodeCheck.Wait -> {
                    reject(PasscodeMessage.Wait(result.remaining.wholeSecondsUp()))
                    countDown()
                }

                is PasscodeCheck.Failed -> {
                    reject(PasscodeMessage.Failed(result.error))
                }
            }
        }
    }

    private fun reject(message: PasscodeMessage) {
        mutableEntry.update { it.copy(isChecking = false, message = message, rejections = it.rejections + 1) }
    }

    /** Counts a running wait down by the second, then clears it; nothing if none runs or one is counted already. */
    private fun countDown() {
        if (countdown?.isActive == true) return
        if (remainingWait() <= Duration.ZERO) {
            mutableEntry.update { if (it.message is PasscodeMessage.Wait) it.copy(message = null) else it }
            return
        }
        countdown =
            scope.launch {
                while (true) {
                    val remaining = remainingWait()
                    if (remaining <= Duration.ZERO) break
                    mutableEntry.update { it.copy(message = PasscodeMessage.Wait(remaining.wholeSecondsUp())) }
                    // To the next whole second of the wait, so the count changes on time.
                    val toNextSecond = remaining - (remaining.wholeSecondsUp() - 1).seconds
                    delay(toNextSecond)
                }
                mutableEntry.update { if (it.message is PasscodeMessage.Wait) it.copy(message = null) else it }
            }
    }
}

/** Whole seconds, rounded up, so a wait shows "0:01" until it is really over. */
private fun Duration.wholeSecondsUp(): Long = (inWholeMilliseconds + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND

private const val MILLIS_PER_SECOND = 1_000L
