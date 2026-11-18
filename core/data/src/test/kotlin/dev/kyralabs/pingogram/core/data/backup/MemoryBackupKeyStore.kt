package dev.kyralabs.pingogram.core.data.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [BackupKeyStore] in memory: [kept] is the key kept; [fails] makes keeping it fail. */
internal class MemoryBackupKeyStore : BackupKeyStore {
    val kept = MutableStateFlow<BackupKey?>(null)
    var fails = false

    override val hasKey = kept.map { it != null }

    override fun keep(key: BackupKey): Boolean {
        if (!fails) kept.value = key
        return !fails
    }

    override fun key(): BackupKey? = kept.value
}
