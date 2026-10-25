package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.CallDataSaving
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Data and storage page in each of its states; screen captures, so the dialog's window shows. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DataStorageScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, DataStorageUiState())

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.storage)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.storage)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, SettingsPreviewData.storageError)

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, SettingsPreviewData.storageError)

    @Test
    fun clearCacheDialogLight() = capture("clearCacheDialogLight", ThemeMode.Light, withDialog)

    @Test
    fun clearCacheDialogDark() = capture("clearCacheDialogDark", ThemeMode.Dark, withDialog)

    @Test
    fun dataSaverLight() =
        capture("dataSaverLight", ThemeMode.Light, SettingsPreviewData.storage.copy(isDataSaverOn = true))

    @Test
    fun clearingLight() =
        capture("clearingLight", ThemeMode.Light, SettingsPreviewData.storage.copy(isClearingCache = true))

    /** The whole page with "Use less data for calls" below the network rows: a window tall enough for all of it. */
    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    fun callsLight() = capture("callsLight", ThemeMode.Light, SettingsPreviewData.storage, CallDataSaving.OnMobileData)

    @Test
    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    fun callsDark() = capture("callsDark", ThemeMode.Dark, SettingsPreviewData.storage, CallDataSaving.Never)

    private val withDialog = SettingsPreviewData.storage.copy(isClearCacheDialogOpen = true)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: DataStorageUiState,
        callDataSaving: CallDataSaving? = null,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        DataStorageScreen(
            state = state,
            onEvent = {},
            onOpenAutoDownload = {},
            onOpenProxies = {},
            onOpenNetworkUsage = {},
            onBack = {},
            callDataSaving = callDataSaving,
        )
    }
}
