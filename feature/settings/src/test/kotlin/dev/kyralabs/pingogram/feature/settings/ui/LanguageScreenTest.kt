package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the Language page shows and sends: the system's language and each shipped one as radio rows, named in
 * themselves, the selected one selected; choosing another; and the row that opens Android's app language settings
 * where Android has them. The screen is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class LanguageScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<LanguageEvent>()
    private var androidSettingsOpened = 0

    private fun setScreen(
        selected: String? = null,
        androidSettings: Boolean = true,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                LanguageScreen(
                    state = LanguageUiState.Content(persistentListOf("en", "de"), selected),
                    onEvent = { events += it },
                    onBack = {},
                    systemLanguage = "fr-FR",
                    onOpenAndroidSettings = if (androidSettings) ({ androidSettingsOpened++ }) else null,
                )
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private val radioButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun `lists the system default and each language in itself, the chosen one selected`() {
        setScreen(selected = "de")

        composeRule.onNodeWithText(string(R.string.settings_language_system)).assertIsNotSelected().assert(radioButton)
        // The system's language, French, named in French below "System default".
        composeRule.onNodeWithText("Français (France)", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("English").assertIsNotSelected()
        // German in German, and in the language of the screens below it.
        composeRule.onNodeWithText("Deutsch").assertIsSelected().assert(radioButton)
        composeRule.onNodeWithText("German", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `choosing a language or the system default sends it`() {
        setScreen(selected = "de")

        composeRule.onNodeWithText("English").performClick()
        composeRule.onNodeWithText(string(R.string.settings_language_system)).performClick()

        assertEquals(listOf(LanguageEvent.Select("en"), LanguageEvent.Select(null)), events)
    }

    @Test
    fun `choosing the selected language again sends nothing`() {
        setScreen(selected = null)

        composeRule.onNodeWithText(string(R.string.settings_language_system)).assertIsSelected().performClick()

        assertEquals(emptyList<LanguageEvent>(), events)
    }

    @Test
    fun `opens Android's app language settings where Android has them`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.settings_language_android_settings)).performClick()

        assertEquals(1, androidSettingsOpened)
    }

    @Test
    fun `before Android 13 there are no Android settings to open`() {
        setScreen(androidSettings = false)

        composeRule.onNodeWithText(string(R.string.settings_language_android_settings)).assertDoesNotExist()
    }
}
