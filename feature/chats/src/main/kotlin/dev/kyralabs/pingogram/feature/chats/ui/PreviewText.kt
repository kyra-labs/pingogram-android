package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chats.R

/** A row's preview: [styled] for the second line, [text] for the row's description. */
internal class PreviewText(
    val styled: AnnotatedString,
    val text: String,
)

/**
 * The second line of a row: typing in `primary`; "Draft:" in `tertiary` before the draft; the sender in `onSurface`
 * before the message, which is in `error` if it failed to send ([isFailed]); the rest in the row's `onSurfaceVariant`.
 * Built again only when its texts or colors change.
 */
@Composable
internal fun previewText(
    preview: ChatPreview,
    isFailed: Boolean,
): PreviewText {
    val colors = MaterialTheme.colorScheme
    val parts =
        when (preview) {
            is ChatPreview.Action -> {
                PreviewParts(text = preview.text.resolve(), textColor = colors.primary)
            }

            is ChatPreview.Draft -> {
                PreviewParts(stringResource(R.string.chat_list_draft), colors.tertiary, preview.text.resolve())
            }

            is ChatPreview.Message -> {
                PreviewParts(
                    label = preview.sender?.let { stringResource(R.string.chat_list_sender, it.resolve()) },
                    labelColor = colors.onSurface,
                    text = preview.text.resolve(),
                    textColor = if (isFailed) colors.error else Color.Unspecified,
                )
            }

            ChatPreview.None -> {
                PreviewParts(text = "")
            }
        }
    return remember(parts) { parts.toPreviewText() }
}

/** The label ("Draft:", "Alice:") and the text of a preview, each in its color. */
private data class PreviewParts(
    val label: String? = null,
    val labelColor: Color = Color.Unspecified,
    val text: String,
    val textColor: Color = Color.Unspecified,
) {
    fun toPreviewText(): PreviewText {
        val styled =
            buildAnnotatedString {
                if (label != null) {
                    withStyle(SpanStyle(color = labelColor)) { append(label) }
                    append(' ')
                }
                withStyle(SpanStyle(color = textColor)) { append(text) }
            }
        return PreviewText(styled, if (label != null) "$label $text" else text)
    }
}

/**
 * One line, in `bodyMedium`, with the sender inline: Material's two-line list item, 72 dp rows of one height
 * (`docs/DESIGN_SYSTEM.md` §6–7). Screen readers get the row's description instead.
 */
@Composable
internal fun ChatPreviewText(preview: PreviewText) {
    Text(
        text = preview.styled,
        modifier = Modifier.clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        style = MaterialTheme.typography.bodyMedium,
    )
}
