package dev.kyralabs.pingogram

import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.graphics.ColorUtils
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * [MainActivity] through a rotation, a fold (its smallest width changing) and a dark mode switch, which its
 * manifest's `configChanges` hand to it as configuration changes: the same activity stays, with the tab it showed,
 * and the app takes the dark theme without being created again.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class MainActivityConfigurationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready).apply {
            // TDLib's answer once a chat list is loaded completely: the chat list is empty.
            on(TdApi.LoadChats::class, TdResult.Failure(TdError(404, "Not Found")))
        }

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `a rotation keeps the activity and the tab it shows`() =
        onSettingsTab { controller ->
            val activity = controller.get()

            controller.configurationChange(
                activity.configuration {
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                    screenWidthDp = HEIGHT_DP
                    screenHeightDp = WIDTH_DP
                },
            )

            assertSame(activity, controller.get())
            awaitSettingsTab()
        }

    @Test
    fun `unfolding keeps the activity and the tab it shows`() =
        onSettingsTab { controller ->
            val activity = controller.get()

            controller.configurationChange(
                activity.configuration {
                    screenWidthDp = UNFOLDED_DP
                    screenHeightDp = UNFOLDED_DP
                    smallestScreenWidthDp = UNFOLDED_DP
                    screenLayout = Configuration.SCREENLAYOUT_SIZE_XLARGE or Configuration.SCREENLAYOUT_LONG_NO
                },
            )

            assertSame(activity, controller.get())
            awaitSettingsTab()
        }

    @Test
    fun `dark mode themes the app again without creating the activity again`() =
        onSettingsTab { controller ->
            val activity = controller.get()
            assertTrue("light before", activity.windowBackgroundLuminance() > HALF)

            controller.configurationChange(
                activity.configuration {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
                },
            )

            assertSame(activity, controller.get())
            // The theme cross-fades to the dark scheme; the window's background follows it.
            composeRule.waitUntil(TIMEOUT_MILLIS) { activity.windowBackgroundLuminance() < DARK }
            awaitSettingsTab()
        }

    /** Starts the activity, opens the Settings tab and runs [block], then destroys the activity. */
    private fun onSettingsTab(block: (ActivityController<MainActivity>) -> Unit) {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            awaitText(R.string.nav_chats)
            composeRule.onNode(settingsTab).performClick()
            awaitSettingsTab()
            block(controller)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private val settingsTab: SemanticsMatcher
        get() =
            (hasText(string(R.string.nav_settings)) or hasContentDescription(string(R.string.nav_settings))) and
                isSelectable()

    private fun awaitSettingsTab() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(settingsTab)
                .fetchSemanticsNodes()
                .any { node -> node.config.getOrElseNullable(SemanticsProperties.Selected) { null } == true }
        }
    }

    private fun awaitText(
        @StringRes id: Int,
    ) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(string(id))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private fun MainActivity.configuration(change: Configuration.() -> Unit): Configuration =
        Configuration(resources.configuration).apply(change)

    /** The luminance of the window's background, which the app sets to its theme's background. */
    private fun MainActivity.windowBackgroundLuminance(): Double {
        composeRule.waitForIdle()
        val background = window.decorView.background as? ColorDrawable ?: return -1.0
        return ColorUtils.calculateLuminance(background.color)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val WIDTH_DP = 411
        const val HEIGHT_DP = 891

        /** A book-style foldable's inner screen. */
        const val UNFOLDED_DP = 840
        const val HALF = 0.5
        const val DARK = 0.1
    }
}
