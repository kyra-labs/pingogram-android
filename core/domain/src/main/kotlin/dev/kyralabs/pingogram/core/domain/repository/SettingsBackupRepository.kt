package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome

/**
 * The app's settings as a backup holds them (`docs/adr/0039-local-backup-and-restore.md`, the `settings` section): the
 * settings that go into backups by their key in the settings file, each a Boolean, Int, Long, Float, Double, String or
 * set of Strings. The passcode, locked chats, one-time hints and prompts, and what is about this phone never do.
 */
interface SettingsBackupRepository {
    /** The settings that go into backups, as they are now; none when the settings cannot be read. */
    suspend fun backedUpSettings(): Map<String, Any>

    /**
     * Replaces the settings that go into backups with [settings]: one [settings] lacks goes back to its default, and
     * what never goes into backups (the passcode above all) stays as it is, even if [settings] holds it. Fails with
     * `DomainError.Unknown` if the settings cannot be written (a full disk).
     */
    suspend fun restoreSettings(settings: Map<String, Any>): Outcome<Unit>
}
