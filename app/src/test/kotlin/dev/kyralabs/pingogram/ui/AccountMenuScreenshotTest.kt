package dev.kyralabs.pingogram.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Settings tab's account menu (`docs/adr/0031-multiple-accounts.md`), opened by a long press on the floating
 * navigation bar: light, dark, AMOLED black, high contrast, right to left and at 200% font scale. Screen captures, so
 * the menu's popup window shows. A plain [Application] keeps app start-up work out of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AccountMenuScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @Test
    fun amoledDark() = capture("amoledDark", ThemeMode.Dark, amoled = true)

    @Test
    fun highContrast() = capture("highContrast", ThemeMode.Light, contrast = Contrast.High)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi-ldrtl")
    fun rtl() = capture("rtl", ThemeMode.Light)

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        themeMode: ThemeMode,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, contrast = contrast, amoled = amoled) {
                Box(Modifier.fillMaxSize()) {
                    PingogramNavigationBar(
                        selected = TopLevelDestination.Chats,
                        onSelect = {},
                        onSearch = {},
                        accountMenu = AccountMenuActions(PreviewMenu, onSwitch = {}, onAdd = {}),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        val settings = RuntimeEnvironment.getApplication().getString(R.string.nav_settings)
        composeRule.onNode(hasText(settings) and isSelectable()).performTouchInput { longClick() }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/${javaClass.name}.$name.png")
    }
}
