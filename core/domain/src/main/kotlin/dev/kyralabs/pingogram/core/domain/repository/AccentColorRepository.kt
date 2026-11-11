package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AccentColorOption
import kotlinx.coroutines.flow.StateFlow

/**
 * The name colors of the user's account (td_api.tl `accentColor`): those Telegram offers, which TDLib announces once
 * per start with `updateAccentColors`, and setting the user's own (`setAccentColor`). Telegram decides who may set
 * which; a refusal (Telegram Premium required) comes back as Telegram's error, never worked around.
 */
interface AccentColorRepository {
    /** The colors users may set, in Telegram's order; the seven built-in ones until TDLib announced them. */
    val options: StateFlow<List<AccentColorOption>>

    /** Sets the user's name color to [id], without a background emoji. */
    suspend fun setAccentColor(id: Int): Outcome<Unit>
}
