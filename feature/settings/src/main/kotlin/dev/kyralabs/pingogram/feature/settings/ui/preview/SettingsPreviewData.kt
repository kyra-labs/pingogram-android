package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.StorageUsage
import dev.kyralabs.pingogram.feature.settings.ui.AccountUiModel
import dev.kyralabs.pingogram.feature.settings.ui.DataStorageUiState
import dev.kyralabs.pingogram.feature.settings.ui.SettingsUiState
import dev.kyralabs.pingogram.feature.settings.ui.StorageUsageState

/** Sample states of the settings screens, for previews and screenshot tests. */
internal object SettingsPreviewData {
    /** An account without a photo, so its placeholder shows (previews load no TDLib files). */
    val account =
        AccountUiModel(
            userId = 42,
            name = "Ada Lovelace",
            photo = null,
            phoneNumber = "+44 7911 123456",
            username = "ada",
        )

    val content = SettingsUiState(account = account)

    val storage =
        DataStorageUiState(
            usage =
                StorageUsageState.Content(
                    StorageUsage(filesSize = 1_534_000_000, fileCount = 3_456, databaseSize = 85_200_000),
                ),
        )

    val storageError = DataStorageUiState(usage = StorageUsageState.Error(DomainError.Network))
}
