package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R

/**
 * One line of a message as a list shows it (the pinned messages bar, a forum's topics): "Alice:" in `onSurface` before
 * the [preview], which takes the text's color; the preview alone without a [sender].
 */
@Composable
internal fun senderPreview(
    sender: UiText?,
    preview: UiText,
): AnnotatedString {
    val senderLabel = sender?.let { stringResource(R.string.preview_sender_label, it.resolve()) }
    val text = preview.resolve()
    val senderColor = MaterialTheme.colorScheme.onSurface
    return remember(senderLabel, text, senderColor) {
        buildAnnotatedString {
            if (senderLabel != null) {
                withStyle(SpanStyle(color = senderColor)) { append(senderLabel) }
                append(' ')
            }
            append(text)
        }
    }
}
