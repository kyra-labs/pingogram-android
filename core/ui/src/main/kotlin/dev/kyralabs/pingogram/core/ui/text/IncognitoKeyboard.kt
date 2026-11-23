package dev.kyralabs.pingogram.core.ui.text

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession

/**
 * [content] whose text fields, the dialogs' and sheets' opened in it included, ask the keyboard not to learn from what
 * is typed while [enabled] (`IME_FLAG_NO_PERSONALIZED_LEARNING`): Privacy and security's "Incognito keyboard"
 * (`REQUIREMENTS.md` FR-X7) around the whole app, and a secret chat's conversation always. The keyboard reads it as a
 * field starts taking input; most keyboards honor it, as Gboard does, but none has to.
 *
 * It always wraps [content], on or off, so turning it on or off keeps everything [content] remembers.
 */
@Composable
fun IncognitoKeyboard(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val incognito by rememberUpdatedState(enabled)
    val interceptor = remember { NoLearningInterceptor { incognito } }
    InterceptPlatformTextInput(interceptor, content)
}

/** Asks for no learning on each input session that starts while [enabled]; passes the others on as they are. */
private class NoLearningInterceptor(
    private val enabled: () -> Boolean,
) : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession,
    ): Nothing = nextHandler.startInputMethod(if (enabled()) NoLearningRequest(request) else request)
}

/** [request], with `IME_FLAG_NO_PERSONALIZED_LEARNING` added to what the field tells the keyboard. */
internal class NoLearningRequest(
    private val request: PlatformTextInputMethodRequest,
) : PlatformTextInputMethodRequest by request {
    override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
        request.createInputConnection(outAttributes).also {
            outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        }
}
