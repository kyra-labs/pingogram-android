package dev.kyralabs.pingogram.core.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.repository.BatterySaverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BatterySaverRepository] on Android's `PowerManager.isPowerSaveMode`, followed through
 * `ACTION_POWER_SAVE_MODE_CHANGED`. The state is read at once, so nothing waits for a broadcast.
 *
 * Shared in the app's scope: one receiver and one question to the power manager serve every collector (each media
 * viewer, stories viewer and call screen), instead of a registration and binder calls on the main thread in the frame
 * each screen's view model was made (`docs/audit/performance-review-2026-11.md`, W6). The receiver stays registered
 * from the first collector for the life of the process: it takes one protected broadcast that only the system sends,
 * rarely, and power saving is followed whenever stickers or the media viewer show.
 */
@Singleton
class AndroidBatterySaverRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) : BatterySaverRepository {
        private val power = context.getSystemService(PowerManager::class.java)

        override val isOn: Flow<Boolean> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            trySend(power?.isPowerSaveMode == true)
                        }
                    }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                    // A protected broadcast, which only the system sends. Not exported would need androidx's
                    // signature permission before Android 13, for nothing.
                    ContextCompat.RECEIVER_EXPORTED,
                )
                trySend(power?.isPowerSaveMode == true)
                awaitClose { context.unregisterReceiver(receiver) }
            }.distinctUntilChanged()
                .shareIn(scope, SharingStarted.Lazily, replay = 1)
    }
