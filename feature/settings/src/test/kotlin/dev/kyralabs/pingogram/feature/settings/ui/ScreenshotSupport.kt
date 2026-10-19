package dev.kyralabs.pingogram.feature.settings.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** A compact phone in portrait (`docs/TESTING.md`). */
const val COMPACT = "w411dp-h891dp-xxhdpi"

/** An expanded window, e.g. a tablet or an unfolded foldable. */
const val EXPANDED = "w840dp-h891dp-xxhdpi"

/** The largest font scale screens must handle without clipping (`REQUIREMENTS.md` §6.3). */
const val FONT_SCALE_200 = 2f

/**
 * Declares [ComponentActivity] to Robolectric so a compose rule can launch it (ui-test-manifest is not a dependency),
 * without the platform action bar, which whole-screen captures would show.
 */
class ComponentActivityRegistration : ExternalResource() {
    override fun before() {
        val application = RuntimeEnvironment.getApplication()
        val packageManager = shadowOf(application.packageManager)
        val activity = packageManager.addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
        activity.theme = android.R.style.Theme_Material_Light_NoActionBar
        packageManager.addOrUpdateActivity(activity)
    }
}

/**
 * Shows [content] in [PingogramTheme] with brand colors in [themeMode], with true-black surfaces if [amoled], and
 * captures the whole screen, dialogs included, as the golden `src/test/snapshots/<test class>.<name>.png`.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun AndroidComposeTestRule<*, *>.captureScreen(
    testClass: Class<*>,
    name: String,
    themeMode: ThemeMode,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    setContent { PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, content = content) }
    waitForIdle()
    captureScreenRoboImage("src/test/snapshots/${testClass.name}.$name.png")
}
