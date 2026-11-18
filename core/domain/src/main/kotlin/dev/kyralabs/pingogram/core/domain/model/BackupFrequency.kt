package dev.kyralabs.pingogram.core.domain.model

/**
 * How often backups write themselves (`docs/adr/0039-local-backup-and-restore.md`, "Automatic backups", owner decision
 * 2026-11-17): never, every day or every week, Weekly by default once a backup folder is kept.
 */
enum class BackupFrequency {
    Off,
    Daily,
    Weekly,
}
