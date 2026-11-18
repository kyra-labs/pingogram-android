package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.BackupFrequency
import dev.kyralabs.pingogram.core.domain.repository.AutomaticBackupRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Schedules the backups that write themselves (`docs/adr/0039-local-backup-and-restore.md`, "Automatic backups"):
 * unique periodic work ([AutomaticBackupWorker]) every day or every week as the user chose, updated in place when the
 * choice changes ([ExistingPeriodicWorkPolicy.UPDATE], which keeps the time it was first enqueued, so a start does not
 * move its next run) and cancelled while it is Off or cannot run (no folder, or no key kept yet: a backup the user
 * writes with a password keeps one). [start] waits [START_DELAY] first, as the storage clean-up does, so
 * the chat list's first page comes first.
 */
@Singleton
class AutomaticBackups internal constructor(
    private val backups: AutomaticBackupRepository,
    private val work: AutomaticBackupWork,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        backups: AutomaticBackupRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : this(backups, WorkManagerAutomaticBackupWork(context), scope)

    /** Follows the choice from [START_DELAY] on, in every process; once. */
    fun start() {
        scope.launch {
            delay(START_DELAY)
            combine(backups.frequency, backups.isReady) { frequency, ready ->
                if (ready) frequency else BackupFrequency.Off
            }.distinctUntilChanged()
                .collect { frequency ->
                    if (frequency == BackupFrequency.Off) work.cancel() else work.schedule(frequency)
                }
        }
    }

    internal companion object {
        /** How long after the process started the automatic backups are first scheduled. */
        val START_DELAY: Duration = 5.seconds
    }
}

/** Where the automatic backups' work is scheduled; WorkManager in the app, a fake in tests. */
internal interface AutomaticBackupWork {
    /** Runs a backup every day or every week, from now on; replaces what was scheduled. */
    fun schedule(frequency: BackupFrequency)

    fun cancel()
}

/** [AutomaticBackupWork] on WorkManager: unique periodic work, no constraint of WorkManager's own (see the worker). */
internal class WorkManagerAutomaticBackupWork(
    private val context: Context,
) : AutomaticBackupWork {
    override fun schedule(frequency: BackupFrequency) {
        val days = if (frequency == BackupFrequency.Daily) DAILY else WEEKLY
        val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(days, TimeUnit.DAYS).build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    private companion object {
        const val NAME = "automatic_backup"
        const val DAILY = 1L
        const val WEEKLY = 7L
    }
}
