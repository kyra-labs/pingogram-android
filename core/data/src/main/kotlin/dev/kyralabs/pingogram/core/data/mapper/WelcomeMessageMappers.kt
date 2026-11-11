package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.WelcomeDraft
import dev.kyralabs.pingogram.core.domain.model.WelcomeMessage
import dev.kyralabs.pingogram.core.domain.model.WelcomePhoto
import org.drinkless.tdlib.TdApi

/** A text, or a photo with its caption; any other content is kept by its id only, as one Pingogram cannot show. */
internal fun TdApi.WelcomeMessage.toWelcomeMessage(): WelcomeMessage =
    when (val content = content) {
        is TdApi.MessageText -> WelcomeMessage(id, content.text.toFormattedText())
        is TdApi.MessagePhoto -> WelcomeMessage(id, content.caption.toFormattedText(), content.photo.toPhoto())
        else -> WelcomeMessage(id, FormattedText.Empty, isSupported = false)
    }

/**
 * The content that sends [this] draft: `inputMessageText` without a link preview's options, or `inputMessagePhoto`
 * with the caption below it, no spoiler and no self-destruct. A picked photo goes as a local file of unknown size
 * (Telegram measures an uploaded photo itself); a kept one by its largest size's file, at that size.
 */
internal fun WelcomeDraft.toInputMessageContent(): TdApi.InputMessageContent {
    val formatted = text.toTdFormattedText()
    val input =
        when (val photo = photo) {
            null -> {
                return TdApi.InputMessageText(formatted, null, false)
            }

            is WelcomePhoto.Picked -> {
                TdApi.InputPhoto(TdApi.InputFileLocal(photo.path), null, null, intArrayOf(), 0, 0)
            }

            is WelcomePhoto.Kept -> {
                val largest = photo.photo.sizes.maxBy { it.width.toLong() * it.height }
                val file = TdApi.InputFileId(largest.file.id)
                TdApi.InputPhoto(file, null, null, intArrayOf(), largest.width, largest.height)
            }
        }
    return TdApi.InputMessagePhoto(input, formatted, false, null, false)
}
