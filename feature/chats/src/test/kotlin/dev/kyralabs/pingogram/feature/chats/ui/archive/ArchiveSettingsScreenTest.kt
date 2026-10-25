package dev.kyralabs.pingogram.feature.chats.ui.archive

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.preview.ArchiveSettingsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the archive settings page sends (FR-L9): each switch the settings with its change, the row's styles as radio
 * buttons, "Try again" after a failure, and back; switches Telegram does not offer are disabled. Every state passes
 * the accessibility checks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ArchiveSettingsScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ArchiveSettingsEvent>()
    private var backs = 0

    @Test
    fun `each switch sends the settings with its change`() {
        setScreen(ArchiveSettingsPreviewData.content)
        val settings = (ArchiveSettingsPreviewData.content.settings as ArchiveSettingsContent.Content).settings

        switch(R.string.archive_settings_new_chats).performClick()
        switch(R.string.archive_settings_keep_unmuted).performClick()
        switch(R.string.archive_settings_keep_folders).performClick()

        assertEquals(
            listOf(
                ArchiveSettingsEvent.Change(settings.copy(archiveAndMuteNewChatsFromNonContacts = false)),
                ArchiveSettingsEvent.Change(settings.copy(keepUnmutedChatsArchived = true)),
                ArchiveSettingsEvent.Change(settings.copy(keepChatsFromFoldersArchived = false)),
            ),
            events,
        )
    }

    @Test
    fun `the switches are read as switches with their state`() {
        setScreen(ArchiveSettingsPreviewData.content)

        switch(R.string.archive_settings_new_chats)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        switch(R.string.archive_settings_keep_unmuted)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    @Test
    fun `where Telegram does not offer it, archiving new chats is disabled and says why`() {
        setScreen(ArchiveSettingsPreviewData.unavailable)

        switch(R.string.archive_settings_new_chats).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.archive_settings_new_chats_unavailable)).assertExists()
    }

    @Test
    fun `keeping every unmuted chat keeps the folders' too, and that switch waits`() {
        setScreen(ArchiveSettingsPreviewData.unavailable)

        switch(R.string.archive_settings_keep_folders)
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        switch(R.string.archive_settings_keep_unmuted).assertIsEnabled()
    }

    @Test
    fun `the row's styles are radio buttons, the current one selected`() {
        setScreen(ArchiveSettingsPreviewData.content)

        radio(R.string.archive_settings_row_compact).performScrollTo().assertIsSelected()
        radio(R.string.archive_settings_row_hidden).performScrollTo().performClick()

        assertEquals(listOf(ArchiveSettingsEvent.SetRowStyle(ArchiveRowStyle.Hidden)), events)
    }

    @Test
    fun `the row's styles show while Telegram's settings load or failed`() {
        setScreen(ArchiveSettingsPreviewData.loading)
        radio(R.string.archive_settings_row_full).assertExists()

        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.onNodeWithContentDescription(string(R.string.archive_settings_loading)).assertExists()
    }

    @Test
    fun `a failure offers to try again`() {
        setScreen(ArchiveSettingsPreviewData.error)

        composeRule.onNodeWithText(string(R.string.chat_list_retry)).performClick()

        assertEquals(listOf(ArchiveSettingsEvent.Retry), events)
    }

    @Test
    fun `back leaves the page`() {
        setScreen(ArchiveSettingsPreviewData.content)

        composeRule.onNodeWithContentDescription(string(R.string.chat_list_archive_back)).performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `every control has a name and a touch target`() {
        setScreen(ArchiveSettingsPreviewData.content)
        composeRule.assertAccessible()

        listOf(ArchiveSettingsPreviewData.unavailable, ArchiveSettingsPreviewData.error).forEach {
            state = it
            composeRule.waitForIdle()
            composeRule.assertAccessible()
        }
    }

    @Test
    fun `a disabled switch sends nothing`() {
        setScreen(ArchiveSettingsPreviewData.unavailable)

        switch(R.string.archive_settings_new_chats).performClick()

        assertEquals(emptyList<ArchiveSettingsEvent>(), events)
    }

    private var state by mutableStateOf(ArchiveSettingsUiState())

    private fun setScreen(initial: ArchiveSettingsUiState) {
        state = initial
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ArchiveSettingsScreen(state = state, onEvent = { events += it }, onBack = { backs++ })
            }
        }
    }

    private fun switch(
        @StringRes title: Int,
    ): SemanticsNodeInteraction =
        composeRule.onNode(
            hasText(string(title)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
            useUnmergedTree = false,
        )

    private fun radio(
        @StringRes label: Int,
    ): SemanticsNodeInteraction =
        composeRule.onNode(hasText(string(label)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)
}
