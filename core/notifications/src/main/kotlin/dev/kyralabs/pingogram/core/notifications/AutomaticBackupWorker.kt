package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AutomaticBackupRepository

/**
 * Writes one automatic backup (`docs/adr/0039-local-backup-and-restore.md`) with the kept key, keeping the folder's
 * newest 3, when the phone is charging or its battery is not low ([batteryAllows]): WorkManager's constraints all must
 * hold, so the worker checks that either does itself and tries again later ([Result.retry]) when neither does, or when
 * the backup cannot be written now.
 */
@HiltWorker
class AutomaticBackupWorker
    internal constructor(
        context: Context,
        parameters: WorkerParameters,
        private val backups: AutomaticBackupRepository,
        private val batteryAllows: () -> Boolean,
    ) : CoroutineWorker(context, parameters) {
        @AssistedInject
        internal constructor(
            @Assisted context: Context,
            @Assisted parameters: WorkerParameters,
            backups: AutomaticBackupRepository,
        ) : this(context, parameters, backups, { context.batteryAllows() })

        override suspend fun doWork(): Result =
            when {
                !batteryAllows() -> Result.retry()
                backups.backUp() is Outcome.Success -> Result.success()
                else -> Result.retry()
            }
    }

/** Whether the phone charges, or its battery is above the level Android calls low (15%). */
private fun Context.batteryAllows(): Boolean {
    val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return true
    val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return plugged || level < 0 || scale <= 0 || level * PERCENT > LOW_PERCENT * scale
}

private const val PERCENT = 100
private const val LOW_PERCENT = 15
