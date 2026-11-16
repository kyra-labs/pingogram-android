package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.AccountFreeze
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.feature.settings.ui.home.SettingsHomeSummaries
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings home of a frozen account (`docs/DESIGN_SYSTEM.md` §6 "Frozen account"): the notice above the account card
 * in light, dark, AMOLED, high contrast, right to left, a 700 dp window and twice the font size, and without a deletion
 * date.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FrozenAccountScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @Test
    fun amoledDark() = capture("amoledDark", ThemeMode.Dark, amoled = true)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = captureAudit(ScreenshotVariant.Medium)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light)

    @Test
    fun noDeletionDate() = capture("noDeletionDate", ThemeMode.Light, frozen.copy(deletesAt = 0))

    private fun captureAudit(variant: ScreenshotVariant) =
        composeRule.captureScreen(javaClass, variant.goldenName, variant) {
            SettingsScreen(
                state = SettingsPreviewData.content,
                onEvent = {},
                onOpenPage = {},
                summaries = SettingsHomeSummaries(freeze = frozen),
            )
        }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        freeze: AccountFreeze = frozen,
        amoled: Boolean = false,
    ) = composeRule.captureScreen(javaClass, name, themeMode, amoled) {
        SettingsScreen(
            state = SettingsPreviewData.content,
            onEvent = {},
            onOpenPage = {},
            summaries = SettingsHomeSummaries(freeze = freeze),
        )
    }

    private companion object {
        // Noon UTC, so the date is the same in every time zone the tests run in.
        val frozen =
            AccountFreeze(frozenAt = 1_798_000_000, deletesAt = 1_800_532_800, appealLink = "https://t.me/spambot")
    }
}
