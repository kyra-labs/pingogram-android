package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Animation
import kotlinx.coroutines.flow.Flow

/**
 * The GIF tab of the composer's panel (`REQUIREMENTS.md` FR-C5): the GIFs the user saved, as Telegram keeps them for
 * the account (a GIF the user sends joins them by itself), and the bot Telegram names for searching GIFs, which the
 * tab asks through `InlineBotRepository`. Sending one is `AttachmentRepository.sendAnimation`.
 */
interface SavedGifRepository {
    /**
     * The saved GIFs, the most recently used first, read when collected and again whenever Telegram reports that they
     * changed; a failed read emits its `Outcome.Failure` and the next change reads them again.
     */
    fun savedGifs(): Flow<Outcome<List<Animation>>>

    /** Takes [gif] out of the saved GIFs. */
    suspend fun removeSaved(gif: Animation): Outcome<Unit>

    /** The username (without "@") of the bot that searches GIFs, as Telegram names it; null where it names none. */
    suspend fun searchBotUsername(): Outcome<String?>
}
