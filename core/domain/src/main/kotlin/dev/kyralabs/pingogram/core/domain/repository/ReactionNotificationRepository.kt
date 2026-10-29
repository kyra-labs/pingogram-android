package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * The account's notification settings for reactions (Settings → Notifications → Reactions). TDLib announces them at
 * every start (`updateReactionNotificationSettings`) and has no function to read them, so this is a cache.
 */
interface ReactionNotificationRepository {
    /** The settings as TDLib last announced them; null until it has. */
    val settings: StateFlow<ReactionNotificationSettings?>

    /** Makes [settings] the account's; the notification sound Telegram keeps is left as it is. */
    suspend fun setSettings(settings: ReactionNotificationSettings): Outcome<Unit>
}
