package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * Posts the Daily digest (`REQUIREMENTS.md` FR-X23, scheduled by [DailyDigestSchedule]): while it is on, reads it
 * ([DailyDigestReader]) and posts it ([DailyDigestNotificationBuilder]), then schedules the next day's. Turned off
 * meanwhile, it posts nothing and schedules nothing. Nothing is retried: a missed digest waits for the next day.
 */
@HiltWorker
class DailyDigestWorker
    internal constructor(
        context: Context,
        parameters: WorkerParameters,
        private val settings: DailyDigestSettingsRepository,
        private val read: suspend () -> DailyDigest,
        private val post: (DailyDigest) -> Unit,
        private val scheduleNext: (time: LocalTime) -> Unit,
    ) : CoroutineWorker(context, parameters) {
        @AssistedInject
        internal constructor(
            @Assisted context: Context,
            @Assisted parameters: WorkerParameters,
            settings: DailyDigestSettingsRepository,
            reader: DailyDigestReader,
            notifications: DailyDigestNotificationBuilder,
            clock: WallClock,
        ) : this(
            context = context,
            parameters = parameters,
            settings = settings,
            read = reader::read,
            post = { digest -> notifications.post(digest, clock.now()) },
            scheduleNext = { time ->
                WorkManagerDailyDigestWork(context).enqueue(nextDigestAt(time, clock.now(), clock.zone()))
            },
        )

        override suspend fun doWork(): Result {
            val current = settings.settings.first()
            if (current.enabled) {
                post(read())
                scheduleNext(current.time)
            }
            return Result.success()
        }
    }
