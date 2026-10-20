package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmojiGroup
import kotlinx.coroutines.flow.Flow

/**
 * The emoji of the composer's emoji panel (`REQUIREMENTS.md` FR-C5): Unicode's emoji, bundled with the app, without
 * those the device's fonts cannot draw. Read once per process, off the main thread.
 */
interface EmojiCatalogRepository {
    /** Every emoji the device can show, by Unicode's groups; never fails (a group may be empty). */
    suspend fun catalog(): List<EmojiGroup>
}

/**
 * The emoji the user picked last in the emoji panel (`REQUIREMENTS.md` FR-C5), kept on this device across logouts like
 * the Appearance settings, as a keyboard keeps them.
 */
interface RecentEmojiRepository {
    /** The recent emoji, the latest first, at most [MAX_RECENT_EMOJI]; then every change. Never fails: empty then. */
    val recentEmoji: Flow<List<String>>

    /**
     * Puts [emoji] first among the recent ones (moving it if it was there already), dropping the oldest beyond
     * [MAX_RECENT_EMOJI]. Fails with `DomainError.Unknown` if it cannot be saved (a full disk).
     */
    suspend fun addRecent(emoji: String): Outcome<Unit>
}

/** How many recent emoji the panel keeps: about four rows of a phone's panel. */
const val MAX_RECENT_EMOJI = 32
