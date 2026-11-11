package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Whether the user read, once, that the AI editor sends their text to Telegram's AI (`docs/DESIGN_SYSTEM.md` §6
 * "AI text editor"). Kept on the device, for every account.
 */
interface AiEditorConsentRepository {
    /** True once the user went past the explanation. */
    val acknowledged: Flow<Boolean>

    /** Records that the user read it. */
    suspend fun acknowledge(): Outcome<Unit>
}
