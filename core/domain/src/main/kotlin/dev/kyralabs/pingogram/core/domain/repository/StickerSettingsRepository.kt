package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * How stickers behave on this device (Settings → Stickers): which stickers the composer suggests for an emoji typed
 * alone ([StickerSettings.suggestions]) and whether animated stickers loop ([StickerSettings.loop]).
 */
interface StickerSettingsRepository {
    /** The settings, then every change. Never fails: unreadable settings read as the defaults. */
    val settings: Flow<StickerSettings>

    /** Saves which stickers are suggested; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setSuggestions(suggestions: StickerSuggestions): Outcome<Unit>

    /** Saves whether animated stickers loop; fails as [setSuggestions] does. */
    suspend fun setLoop(loop: Boolean): Outcome<Unit>
}

/**
 * Sticker settings: the stickers suggested above the composer while it holds one emoji alone ([suggestions], the
 * user's own sets by default), and whether animated stickers and custom emoji play in a loop ([loop], on by default;
 * off, each plays once where it shows, then rests on its first frame).
 */
data class StickerSettings(
    val suggestions: StickerSuggestions = StickerSuggestions.MySets,
    val loop: Boolean = true,
)

/**
 * Which stickers the composer suggests for an emoji: from every public set on Telegram as well as the user's
 * ([AllSets]), from the user's installed sets only ([MySets]), or none ([Off]).
 */
enum class StickerSuggestions {
    AllSets,
    MySets,
    Off,
}
