package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.StickerSetDetails
import dev.kyralabs.pingogram.core.domain.model.StickerSetKind
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import kotlinx.coroutines.flow.Flow

/**
 * The user's sticker and custom emoji sets as Settings → Stickers manages them (`REQUIREMENTS.md` FR-C5): the
 * installed, archived and trending sets of a [StickerSetKind], one set with its stickers, and the changes Telegram
 * keeps for the account (installing, archiving, removing, reordering). The sticker panel reads the installed sticker
 * sets through `StickerRepository.installedSets`.
 *
 * Lists are read when collected and again whenever Telegram reports a change; a failed read emits its
 * `Outcome.Failure` and the next change reads it again.
 */
@Suppress("TooManyFunctions") // Every list and change of Settings → Stickers: one repository, as its pages share it.
interface StickerSetRepository {
    /** The sets of [kind] the user installed, in their order. */
    fun installedSets(kind: StickerSetKind): Flow<Outcome<List<StickerSetSummary>>>

    /** The sets of [kind] the user archived, most recently archived first (Telegram's first 100). */
    fun archivedSets(kind: StickerSetKind = StickerSetKind.Stickers): Flow<Outcome<List<StickerSetSummary>>>

    /** The sets of [kind] trending on Telegram now, in Telegram's order (its first page). */
    fun trendingSets(kind: StickerSetKind = StickerSetKind.Stickers): Flow<Outcome<List<StickerSetSummary>>>

    /** Set [setId] with its stickers, again whenever it changes (installed, archived, edited by its owner). */
    fun stickerSet(setId: Long): Flow<Outcome<StickerSetDetails>>

    /**
     * The sets of [kind] on Telegram that match [query] (Telegram's catalog, `searchStickerSets`), in Telegram's order;
     * installed ones among them say so. The words go to Telegram, as its own apps send them.
     */
    suspend fun searchSets(
        query: String,
        kind: StickerSetKind = StickerSetKind.Stickers,
    ): Outcome<List<StickerSetSummary>>

    /** The id of the set named [name] (`t.me/addstickers/<name>`); `DomainError.NotFound` if there is none. */
    suspend fun findSet(name: String): Outcome<Long>

    /** Installs set [setId] (from the trending or archived sets, or a link), at the top of the user's sets. */
    suspend fun install(setId: Long): Outcome<Unit>

    /** Archives installed set [setId]: it leaves the panel, and comes back with [install]. */
    suspend fun archive(setId: Long): Outcome<Unit>

    /** Removes set [setId] from the user's sets, installed or archived. */
    suspend fun remove(setId: Long): Outcome<Unit>

    /** Puts the installed sets of [kind] in the order of [setIds]. */
    suspend fun reorder(
        setIds: List<Long>,
        kind: StickerSetKind = StickerSetKind.Stickers,
    ): Outcome<Unit>

    /** Tells Telegram the user saw trending sets [setIds], which then no longer count as new. */
    suspend fun markTrendingViewed(setIds: List<Long>): Outcome<Unit>
}
