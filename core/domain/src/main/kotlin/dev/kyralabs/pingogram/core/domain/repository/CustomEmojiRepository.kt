package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.StateFlow

/**
 * The custom emoji of the texts on screen (`docs/ARCHITECTURE.md`, Custom emoji): their stickers, loaded once per
 * TDLib session and kept in memory by id, for the conversation to draw. Only what the screen shows is asked for, never
 * a whole history.
 */
interface CustomEmojiRepository {
    /** Every custom emoji loaded so far in this session, by id; emptied when TDLib closes (file ids are its own). */
    val customEmoji: StateFlow<ImmutableMap<Long, CustomEmoji>>

    /**
     * Loads the custom emoji [customEmojiIds] into [customEmoji], those not loaded yet: in requests of at most 200 ids
     * (TDLib's limit), none for ids already on their way or that TDLib did not know before, so callers may ask for
     * what they show as often as it changes. Cancelling stops waiting; those ids are asked for again next time. Fails
     * with the first request that failed (`DomainError.Network` without an answer in time, `DomainError.FloodWait`);
     * the ids of the requests that worked are loaded all the same.
     */
    suspend fun load(customEmojiIds: Collection<Long>): Outcome<Unit>
}
