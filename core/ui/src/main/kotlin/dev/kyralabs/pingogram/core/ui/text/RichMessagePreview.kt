package dev.kyralabs.pingogram.core.ui.text

import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.core.ui.R

/** A rich message's first text (its title or first paragraph), else the kind of its first media. */
internal fun richMessageText(message: RichMessage): UiText {
    message.firstText()?.let { return previewOf(it) }
    val kind =
        when (message.media().firstOrNull()) {
            is RichBlock.Photo -> R.string.preview_photo
            is RichBlock.Video -> R.string.preview_video
            is RichBlock.Animation -> R.string.preview_gif
            null -> R.string.preview_rich_message
        }
    return UiText.resource(kind)
}
