package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NetworkStatus
import dev.kyralabs.pingogram.core.domain.model.NetworkType
import kotlinx.coroutines.flow.Flow

/** The app's own settings of calls, kept on the device (Settings → Data and storage). */
interface CallSettingsRepository {
    /** When calls use less data, then every change. Never fails: a setting that cannot be read is the default. */
    val dataSaving: Flow<CallDataSaving>

    /** Saves [saving]; fails with `DomainError.Unknown` if it cannot be written (a full disk). */
    suspend fun setDataSaving(saving: CallDataSaving): Outcome<Unit>
}

/**
 * When calls use less data: the video they send at most 360p at 15 frames a second, as on a roaming network or with
 * Data Saver (`VideoQuality.Low` in `feature:calls`). The audio is the engine's own, which no setting changes.
 */
enum class CallDataSaving {
    /** As the network allows (the default). */
    Never,

    /** On mobile data, roaming and other metered networks; not on Wi-Fi. */
    OnMobileData,

    /** On every network. */
    Always,
    ;

    /** Whether calls over [network] use less data. */
    fun appliesTo(network: NetworkStatus): Boolean =
        when (this) {
            Never -> {
                false
            }

            Always -> {
                true
            }

            OnMobileData -> {
                network.isMetered || network.type == NetworkType.Mobile ||
                    network.type == NetworkType.Roaming
            }
        }
}
