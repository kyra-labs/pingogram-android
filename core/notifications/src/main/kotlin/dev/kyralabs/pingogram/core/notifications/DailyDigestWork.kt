package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** WorkManager's side of the Daily digest's scheduling, a seam for tests. */
internal interface DailyDigestWork {
    /**
     * Makes the digest at [at] the only one with work scheduled, besides one whose time has passed and that still waits
     * to run; none at all when [at] is null.
     */
    suspend fun sync(at: Instant?)

    /** Schedules the digest at [at], kept as it is when it already is. */
    fun enqueue(at: Instant)
}

/**
 * [DailyDigestWork] as one-time work at each digest's time, named by it, so the same time is scheduled once however
 * often it is asked for, and a new time replaces the old. Inexact: WorkManager runs it when Android lets it, without
 * an alarm or the exact-alarm permission, so work whose time passed may still wait (Doze) when the app starts and asks
 * for the next time: it is kept.
 */
internal class WorkManagerDailyDigestWork(
    private val context: Context,
    private val now: () -> Instant = Instant::now,
) : DailyDigestWork {
    override suspend fun sync(at: Instant?) {
        val manager = WorkManager.getInstance(context)
        val wanted = at?.let(::workName)
        val overdue = { info: WorkInfo -> at != null && info.tags.any { isOverdue(it) } }
        manager
            .getWorkInfosByTagFlow(TAG)
            .first()
            .filter { it.state == WorkInfo.State.ENQUEUED && wanted !in it.tags && !overdue(it) }
            .forEach { manager.cancelWorkById(it.id) }
        at?.let(::enqueue)
    }

    override fun enqueue(at: Instant) {
        val name = workName(at)
        val request =
            OneTimeWorkRequestBuilder<DailyDigestWorker>()
                .setInitialDelay(Duration.between(now(), at).coerceAtLeast(Duration.ZERO))
                .addTag(TAG)
                .addTag(name)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    /** Whether [tag] is a digest's work name ([workName]) whose time has come. */
    private fun isOverdue(tag: String): Boolean {
        val seconds = tag.takeIf { it.startsWith(NAME_PREFIX) }?.removePrefix(NAME_PREFIX)?.toLongOrNull()
        return seconds != null && !Instant.ofEpochSecond(seconds).isAfter(now())
    }

    internal companion object {
        const val TAG = "daily_digest"
        private const val NAME_PREFIX = "daily_digest_"

        fun workName(at: Instant) = "$NAME_PREFIX${at.epochSecond}"
    }
}

/** The next time [time] comes in [zone] after [now]: today's, or tomorrow's once today's has passed. */
internal fun nextDigestAt(
    time: LocalTime,
    now: Instant,
    zone: ZoneId,
): Instant {
    val date = now.atZone(zone).toLocalDate()
    val today = date.atTime(time).atZone(zone).toInstant()
    return if (today.isAfter(now)) {
        today
    } else {
        date
            .plusDays(1)
            .atTime(time)
            .atZone(zone)
            .toInstant()
    }
}
