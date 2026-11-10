package dev.kyralabs.pingogram.feature.profile.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the note dialog over a contact's profile (`docs/DESIGN_SYSTEM.md` §6 "Contact note", the owner's board
 * 15b): editing in every look the audit asks for, at 200% font, and a save that failed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ContactNoteDialogScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editing = ContactNoteEditorStates().values.first()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    // A dialog is a window of its own, whose views take their direction from the configuration and the app's RTL
    // support, not from the composition; the library's test app does not declare that support.
    @Test
    @Config(qualifiers = "+ldrtl")
    fun rtl() {
        val info = ApplicationProvider.getApplicationContext<Context>().applicationInfo
        info.flags = info.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
        capture(ScreenshotVariant.Rtl)
    }

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture(ScreenshotVariant.Light, name = "fontScale200")

    @Test
    fun failed() = capture(ScreenshotVariant.Light, ContactNoteEditorStates().values.last(), "failed")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        editor: ProfileNoteEditor = editing,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent { ScreenshotTheme(variant) { ContactNoteDialog(editor, onEvent = {}) } }
        composeRule.waitForIdle()
        captureScreenRoboImage(
            "src/test/snapshots/dev.kyralabs.pingogram.feature.profile.ui.ContactNoteDialogScreenshotTest.$name.png",
        )
    }
}
