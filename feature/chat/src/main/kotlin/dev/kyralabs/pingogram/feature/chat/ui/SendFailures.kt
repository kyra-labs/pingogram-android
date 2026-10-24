package dev.kyralabs.pingogram.feature.chat.ui

import android.os.Build
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.SendingState

/**
 * Tells from one history to the next whether a message being sent failed: more failed messages than before, while
 * the history before had messages being sent. TDLib gives a failed message a new id, so failures are counted rather
 * than followed by id. An older page can bring in a message that failed long ago: that counts only while something
 * else is being sent, and costs a haptic at most.
 *
 * Not thread-safe: one flow collection uses it at a time.
 */
internal class SendFailures {
    private var failed = 0
    private var wasSending = false

    /** Whether [history] has a message that failed since the history before; the first history has none. */
    fun failedSince(history: ChatHistory): Boolean {
        var failedNow = 0
        var sendingNow = false
        for (message in history.messages) {
            when (message.sendingState) {
                is SendingState.Failed -> failedNow++
                SendingState.Pending -> sendingNow = true
                null -> Unit
            }
        }
        val failedSince = wasSending && failedNow > failed
        failed = failedNow
        wasSending = sendingNow
        return failedSince
    }
}

/**
 * The `Reject` haptic of a message that failed to send ([ChatEffect.SendFailed], `docs/DESIGN_SYSTEM.md` §5), on
 * Android 14 and later, which have it. Returns the snackbar's text: none.
 */
internal fun HapticFeedback.rejectSend(): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) performHapticFeedback(HapticFeedbackType.Reject)
    return ""
}

/**
 * The haptic of a quiz's answer once Telegram took it ([PollEffect.QuizAnswered], `docs/DESIGN_SYSTEM.md` §5):
 * `Confirm` for a [correct] one, `Reject` for a wrong one, on Android 14 and later, which have them. Returns the
 * snackbar's text: none.
 */
internal fun HapticFeedback.quizAnswered(correct: Boolean): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        performHapticFeedback(if (correct) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
    }
    return ""
}
