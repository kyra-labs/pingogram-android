package dev.kyralabs.pingogram.core.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.repository.BatterySaverRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BatterySaverRepository] on Android's `PowerManager.isPowerSaveMode`, followed through
 * `ACTION_POWER_SAVE_MODE_CHANGED` while collected. The state is read at once, so nothing waits for a broadcast.
 */
@Singleton
class AndroidBatterySaverRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
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
    }
