package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.DataAmount
import dev.kyralabs.pingogram.core.domain.model.DataKind
import dev.kyralabs.pingogram.core.domain.model.NetworkUsage
import dev.kyralabs.pingogram.feature.settings.ui.network.NetworkUsageUiState
import kotlinx.collections.immutable.persistentMapOf

/** The states of the Network usage page, shared by previews and screenshot tests. */
internal object NetworkUsagePreviewStates {
    private const val MB = 1_048_576L

    // 1 October 2026, at noon UTC so the date is the same in every time zone of the tests.
    private val usage =
        NetworkUsage(
            sinceDate = 1_790_856_000,
            usage =
                persistentMapOf(
                    AutoDownloadNetwork.WiFi to
                        persistentMapOf(
                            DataKind.Messages to DataAmount(3 * MB, 21 * MB),
                            DataKind.Photos to DataAmount(12 * MB, 340 * MB),
                            DataKind.Videos to DataAmount(0, 1_210 * MB),
                            DataKind.VoiceMessages to DataAmount(MB / 2, 4 * MB),
                            DataKind.Files to DataAmount(25 * MB, 96 * MB),
                            DataKind.Other to DataAmount(0, 58 * MB),
                            DataKind.Calls to DataAmount(42 * MB, 44 * MB),
                        ),
                    AutoDownloadNetwork.Mobile to persistentMapOf(DataKind.Messages to DataAmount(MB, 6 * MB)),
                ),
        )

    val wifi = NetworkUsageUiState.Content(usage, AutoDownloadNetwork.WiFi)

    val roaming = NetworkUsageUiState.Content(usage, AutoDownloadNetwork.Roaming)

    val resetDialog = wifi.copy(isResetDialogOpen = true)

    val resetting = wifi.copy(isResetting = true)

    val loading = NetworkUsageUiState.Loading

    val error = NetworkUsageUiState.Error(DomainError.Network)
}
