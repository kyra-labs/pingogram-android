package dev.kyralabs.pingogram.feature.settings.ui.raisetolisten

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Raise to listen page: its master switch, and "Pause when lowered" dimmed while raise to listen is off. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class RaiseToListenScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<RaiseToListenEvent>()

    private fun setScreen(settings: RaiseToListenSettings) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                RaiseToListenScreen(RaiseToListenUiState.Content(settings), onEvent = { events += it }, onBack = {})
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun switch(
        @StringRes title: Int,
    ) = composeRule.onNode(
        hasText(string(title), substring = true) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
    )

    @Test
    fun `the master switch turns raise to listen on and off`() {
        setScreen(RaiseToListenSettings(enabled = false))

        switch(R.string.settings_raise_to_listen_use).performClick()

        assertEquals(listOf(RaiseToListenEvent.SetEnabled(true)), events)
    }

    @Test
    fun `pause when lowered stays in place, disabled, while raise to listen is off`() {
        setScreen(RaiseToListenSettings(enabled = false))

        switch(R.string.settings_pause_when_lowered).assertIsNotEnabled().performClick()

        assertEquals(emptyList<RaiseToListenEvent>(), events)
    }

    @Test
    fun `pause when lowered works while raise to listen is on`() {
        setScreen(RaiseToListenSettings(enabled = true))

        switch(R.string.settings_pause_when_lowered).assertIsEnabled().performClick()

        assertEquals(listOf(RaiseToListenEvent.SetPauseWhenLowered(true)), events)
    }

    @Test
    fun `the info footer says what raising does`() {
        setScreen(RaiseToListenSettings())

        composeRule.onNodeWithText(string(R.string.settings_raise_to_listen_info)).assertExists()
    }
}
