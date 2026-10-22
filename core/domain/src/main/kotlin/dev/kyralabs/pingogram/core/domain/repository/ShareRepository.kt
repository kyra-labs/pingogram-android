package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ShareResult
import dev.kyralabs.pingogram.core.domain.model.SharedContent

/**
 * Sends what the user shared into Pingogram (`REQUIREMENTS.md` FR-N2, direct share) to several chats at once, after
 * the user picked them and tapped send in the share sheet. A share to one chat goes through that chat's composer
 * instead, where the user sends it.
 */
interface ShareRepository {
    /**
     * Sends [content] to each of [chatIds] in turn, [comment] first as a message of its own when it is not blank: the
     * text as a message, or the files (copied into the upload cache once, read with the grant they came with) with
     * the text as their caption, as albums like the composer's. A chat that refuses (no right to write or to send
     * media, a text too long …) is counted in [ShareResult.failed] with the first error; a `FLOOD_WAIT` stops it, the
     * chats left counting as failed. Files that cannot be read fail every chat with `DomainError.FileUnavailable`
     * (or `DomainError.FileTooLarge`), sending nothing.
     */
    suspend fun share(
        chatIds: List<Long>,
        content: SharedContent,
        comment: FormattedText = FormattedText.Empty,
    ): ShareResult
}
