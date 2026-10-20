package dev.kyralabs.pingogram.feature.contacts.ui

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ContactsScreenScreenshotTest {
    @Test
    fun light() = capture(ThemeMode.Light)

    @Test
    fun dark() = capture(ThemeMode.Dark)

    private fun capture(themeMode: ThemeMode) =
        captureRoboImage {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) { ContactsScreen(onOpenNewChat = {}) }
        }
}
