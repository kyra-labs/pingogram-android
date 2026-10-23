package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerSetDetails
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import kotlinx.coroutines.flow.Flow

/**
 * The user's sticker sets as Settings → Stickers manages them (`REQUIREMENTS.md` FR-C5), regular stickers only: the
 * archived and trending sets, one set with its stickers, and the changes Telegram keeps for the account (installing,
 * archiving, removing, reordering). The installed sets are `StickerRepository.installedSets`.
 *
 * Lists are read when collected and again whenever Telegram reports a change; a failed read emits its
 * `Outcome.Failure` and the next change reads it again.
 */
interface StickerSetRepository {
    /** The sets the user archived, most recently archived first (Telegram's first 100). */
    fun archivedSets(): Flow<Outcome<List<StickerSetSummary>>>

    /** The sets trending on Telegram now, in Telegram's order (its first page). */
    fun trendingSets(): Flow<Outcome<List<StickerSetSummary>>>

    /** Set [setId] with its stickers, again whenever it changes (installed, archived, edited by its owner). */
    fun stickerSet(setId: Long): Flow<Outcome<StickerSetDetails>>

    /** The id of the set named [name] (`t.me/addstickers/<name>`); `DomainError.NotFound` if there is none. */
    suspend fun findSet(name: String): Outcome<Long>

    /** Installs set [setId] (from the trending or archived sets, or a link), at the top of the user's sets. */
    suspend fun install(setId: Long): Outcome<Unit>

    /** Archives installed set [setId]: it leaves the panel, and comes back with [install]. */
    suspend fun archive(setId: Long): Outcome<Unit>

    /** Removes set [setId] from the user's sets, installed or archived. */
    suspend fun remove(setId: Long): Outcome<Unit>

    /** Puts the installed sets in the order of [setIds]. */
    suspend fun reorder(setIds: List<Long>): Outcome<Unit>

    /** Tells Telegram the user saw trending sets [setIds], which then no longer count as new. */
    suspend fun markTrendingViewed(setIds: List<Long>): Outcome<Unit>
}
