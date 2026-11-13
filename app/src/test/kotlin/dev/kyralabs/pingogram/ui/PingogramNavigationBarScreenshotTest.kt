package dev.kyralabs.pingogram.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The floating navigation bar of compact windows (`docs/DESIGN_SYSTEM.md` §6.1, owner decision 2026-11-13): every
 * tab's label, the selected tab on its `primary` pill, and the round button beside it, New chat on the Chats tab and
 * Add contact on the Contacts tab, in light and dark, right to left, and on a narrow phone at 200% font scale, where
 * the tabs not selected show their icons instead. A plain [Application] keeps app start-up work out of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PingogramNavigationBarScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatsLight() = capture(ThemeMode.Light, TopLevelDestination.Chats)

    @Test
    fun chatsDark() = capture(ThemeMode.Dark, TopLevelDestination.Chats)

    @Test
    fun contactsLight() = capture(ThemeMode.Light, TopLevelDestination.Contacts)

    @Test
    fun contactsDark() = capture(ThemeMode.Dark, TopLevelDestination.Contacts)

    @Test
    fun rtl() = capture(ThemeMode.Light, TopLevelDestination.Chats, LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2f)
    fun narrowFontScale200() = capture(ThemeMode.Light, TopLevelDestination.Chats)

    private fun capture(
        themeMode: ThemeMode,
        selected: TopLevelDestination,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.setContent {
            // Robolectric's `ldrtl` qualifier does not reach the test activity's composition, so the direction is set here.
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                Bar(themeMode, selected)
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    @Composable
    private fun Bar(
        themeMode: ThemeMode,
        selected: TopLevelDestination,
    ) {
        PingogramTheme(themeMode = themeMode, dynamicColor = false) {
            // On the content's color, with room above and below for the bar's shadow.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = MaterialTheme.spacing.medium),
            ) {
                PingogramNavigationBar(
                    selected = selected,
                    onSelect = {},
                    newChat = {
                        BarPrimaryActions(tab = selected, newChatOpen = false, onNewChat = {}, onNewContact = {})
                    },
                )
            }
        }
    }
}
