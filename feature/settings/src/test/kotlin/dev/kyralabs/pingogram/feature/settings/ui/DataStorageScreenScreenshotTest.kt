package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.CallDataSaving
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsUiState
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

    /** The whole page with "Reset automatic download", "Prefer IPv6" on and "Delete cloud drafts". */
    @Test
    @Config(qualifiers = TALL)
    fun optionsLight() = composeRule.captureScreen(javaClass, "optionsLight", ThemeMode.Light) { Options() }

    @Test
    @Config(qualifiers = TALL)
    fun optionsDark() = composeRule.captureScreen(javaClass, "optionsDark", ThemeMode.Dark) { Options() }

    @Test
    @Config(qualifiers = TALL)
    fun optionsAmoled() = captureOptions(ScreenshotVariant.Amoled)

    @Test
    @Config(qualifiers = TALL)
    fun optionsHighContrast() = captureOptions(ScreenshotVariant.HighContrast)

    @Test
    @Config(qualifiers = TALL)
    fun optionsRtl() = captureOptions(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h1400dp-xxhdpi")
    fun optionsMedium() = captureOptions(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi", fontScale = FONT_SCALE_200)
    fun optionsFontScale200() =
        composeRule.captureScreen(javaClass, "optionsFontScale200", ThemeMode.Light) { Options() }

    @Test
    fun deleteDraftsDialogLight() =
        composeRule.captureScreen(javaClass, "deleteDraftsDialogLight", ThemeMode.Light) {
            Options(DataOptionsUiState(prefersIpv6 = false, isDeleteDraftsDialogOpen = true))
        }

    private fun captureOptions(variant: ScreenshotVariant) =
        composeRule.captureScreen(
            javaClass,
            "options" + variant.goldenName.replaceFirstChar { it.uppercase() },
            variant,
        ) {
            Options()
        }

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

private const val TALL = "w411dp-h1600dp-xxhdpi"

/** The page with automatic download changed from its defaults, so it can be reset, and the options of [options]. */
@Composable
private fun Options(options: DataOptionsUiState = DataOptionsUiState(prefersIpv6 = true)) {
    DataStorageScreen(
        state = SettingsPreviewData.storageChanged,
        onEvent = {},
        onOpenAutoDownload = {},
        onOpenProxies = {},
        onOpenNetworkUsage = {},
        onBack = {},
        callDataSaving = CallDataSaving.Never,
        options = options,
    )
}
