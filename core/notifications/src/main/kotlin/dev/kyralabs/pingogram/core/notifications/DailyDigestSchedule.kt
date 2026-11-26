package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettings
import dev.kyralabs.pingogram.core.domain.repository.DailyDigestSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the Daily digest scheduled (`REQUIREMENTS.md` FR-X23) as its settings say: while it is on, one-time work at
 * its next time ([nextDigestAt]), moved when the time changes and cancelled when it is turned off. Each digest
 * schedules the next day's ([DailyDigestWorker]). No alarm and no exact-alarm permission.
 *
 * [start] waits [START_DELAY] first: scheduling starts WorkManager, which the app's start leaves out.
 */
@Singleton
class DailyDigestSchedule internal constructor(
    private val settings: Flow<DailyDigestSettings>,
    private val work: DailyDigestWork,
    private val clock: WallClock,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: DailyDigestSettingsRepository,
        clock: WallClock,
        @ApplicationScope scope: CoroutineScope,
    ) : this(settings.settings, WorkManagerDailyDigestWork(context), clock, scope)

    /** Follows the settings from [START_DELAY] on, in every process; once. */
    fun start() {
        scope.launch {
            delay(START_DELAY)
            settings
                // Nothing to schedule or cancel while the digest was never on: WorkManager stays unstarted.
                .dropWhile { !it.enabled }
                .collect { current ->
                    val at = if (current.enabled) nextDigestAt(current.time, clock.now(), clock.zone()) else null
                    work.sync(at)
                }
        }
    }

    internal companion object {
        val START_DELAY = 5.seconds
    }
}
