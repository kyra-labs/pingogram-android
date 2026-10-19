package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Open-source licenses page and a library's page: what their rows open. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class OpenSourceLicensesScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `a library's row opens its page`() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                OpenSourceLicensesScreen(
                    state = SettingsPreviewData.openSourceLicenses,
                    onEvent = {},
                    onOpenLibrary = { opened += it },
                    onBack = {},
                )
            }
        }

        composeRule
            .onNodeWithText("TDLib")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        assertEquals(listOf("org.drinkless:tdlib"), opened)
    }

    @Test
    fun `a library's page opens its website and its licenses online`() {
        val events = mutableListOf<OpenSourceLibraryEvent>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                OpenSourceLibraryScreen(
                    state = SettingsPreviewData.openSourceLibrary,
                    onEvent = { events += it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.settings_license_website)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.settings_license_online)).performScrollTo().performClick()

        assertEquals(listOf(OpenSourceLibraryEvent.OpenWebsite, OpenSourceLibraryEvent.OpenLicense("BSL-1.0")), events)
    }
}
