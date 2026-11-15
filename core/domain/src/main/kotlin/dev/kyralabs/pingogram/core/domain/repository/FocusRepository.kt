package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The Focus profiles (board 31), kept on this phone only (owner decision 2026-11-15) and never sent to Telegram: the
 * profiles in the user's order, Focus as the user set it by hand, and the profile used last, which the Quick Settings
 * tile turns on and off.
 */
interface FocusRepository {
    val profiles: Flow<ImmutableList<FocusProfile>>

    /** Focus as the user set it by hand; null while the schedule decides. */
    val manual: Flow<ManualFocus?>

    /** The profile turned on by hand last; null before any was. */
    val lastUsedId: Flow<Long?>

    /** Saves [profile], a new one (id 0) at the end with the next id; returns its id. */
    suspend fun saveProfile(profile: FocusProfile): Outcome<Long>

    /** Deletes profile [id]; Focus set by hand on it ends. */
    suspend fun deleteProfile(id: Long): Outcome<Unit>

    /** Sets Focus by hand ([manual]), or gives the schedule back (null); a profile turned on becomes the last used. */
    suspend fun setManual(manual: ManualFocus?): Outcome<Unit>
}
