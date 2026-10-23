package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import dev.kyralabs.pingogram.core.ui.component.PhotoTask
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Typing on the Edit profile page, what it says about usernames, when Save is enabled, and its confirmations. The
 * screen is stateless: these check what it shows for a state and the events it sends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class EditProfileScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<EditProfileEvent>()
    private var photoPickerOpened = 0

    private fun setScreen(state: EditProfileUiState = SettingsPreviewData.editProfile) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                EditProfileScreen(state = state, onEvent = { events += it }, onPickPhoto = { photoPickerOpened++ })
            }
        }
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ) = RuntimeEnvironment.getApplication().getString(id, *args)

    /** The text field labelled [label], enabled or not. */
    private fun field(
        @StringRes label: Int,
    ) = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText) and hasText(string(label)))

    private fun save() = composeRule.onNodeWithText(string(R.string.settings_edit_profile_save))

    @Test
    fun `shows the profile in its fields, with their limits`() {
        setScreen()

        field(R.string.settings_profile_first_name).assert(hasText("Ada"))
        field(R.string.settings_profile_last_name).assert(hasText("Lovelace"))
        field(R.string.settings_profile_username).assert(hasText("ada")).assert(hasText("@"))
        field(R.string.settings_profile_bio).performScrollTo().assert(hasText("Poetical scientist."))
        composeRule.onNodeWithText("3/64").assertExists()
        composeRule.onNodeWithText("8/64").assertExists()
        composeRule.onNodeWithText("19/70").assertExists()
        // Screen readers hear the counter in words.
        composeRule.onNode(hasContentDescription("3 of 64 characters"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun `typing sends what each field holds`() {
        // The fields show what the view model holds, as it updates them with the events.
        var state by mutableStateOf(SettingsPreviewData.editProfile)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                EditProfileScreen(
                    state = state,
                    onEvent = { event ->
                        events += event
                        state = state.typed(event)
                    },
                    onPickPhoto = {},
                )
            }
        }

        field(R.string.settings_profile_first_name).performTextReplacement("Augusta")
        field(R.string.settings_profile_last_name).performTextReplacement("King")
        field(R.string.settings_profile_username).performTextReplacement("augusta")
        field(R.string.settings_profile_bio).performScrollTo().performTextReplacement("Countess")

        assertEquals(
            listOf(
                EditProfileEvent.FirstNameChanged("Augusta"),
                EditProfileEvent.LastNameChanged("King"),
                EditProfileEvent.UsernameChanged("augusta"),
                EditProfileEvent.BioChanged("Countess"),
            ),
            events,
        )
    }

    @Test
    fun `save is disabled until something changed`() {
        setScreen()

        save().assertIsNotEnabled().performClick()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `save is enabled with valid changes, and saves`() {
        setScreen(SettingsPreviewData.editedProfile(UsernameStatus.Checked(UsernameCheck.Available)))

        save().assertIsEnabled().performClick()

        assertEquals(listOf(EditProfileEvent.Save), events)
    }

    @Test
    fun `save is disabled while something is invalid`() {
        val edited = SettingsPreviewData.editedProfile(UsernameStatus.Checked(UsernameCheck.Available))
        setScreen(edited.copy(fields = edited.fields.copy(firstName = "")))

        save().assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.settings_profile_first_name_required)).assertExists()
    }

    @Test
    fun `while saving, progress replaces save and the fields wait`() {
        setScreen(SettingsPreviewData.editedProfile(UsernameStatus.Unchanged).copy(isSaving = true))

        save().assertDoesNotExist()
        composeRule.onNode(hasContentDescription(string(R.string.settings_edit_profile_saving))).assertExists()
        field(R.string.settings_profile_first_name).assertIsNotEnabled()
    }

    @Test
    fun `an available username says so`() {
        setScreen(SettingsPreviewData.editedProfile(UsernameStatus.Checked(UsernameCheck.Available)))

        composeRule
            .onNodeWithText(
                string(R.string.settings_profile_username_available, "ada_lovelace".bidiIsolated()).bidiIsolated(),
            ).assertExists()
    }

    @Test
    fun `a taken username is the field's error`() {
        setScreen(SettingsPreviewData.editedProfile(UsernameStatus.Checked(UsernameCheck.Taken)))

        val taken = string(R.string.settings_profile_username_taken)
        composeRule.onNodeWithText(taken).assertExists()
        field(R.string.settings_profile_username).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, taken))
        save().assertIsNotEnabled()
    }

    @Test
    fun `every other username state says what it is`() {
        val states =
            mapOf(
                UsernameStatus.Checking to string(R.string.settings_profile_username_checking),
                UsernameStatus.Removed to string(R.string.settings_profile_username_removed),
                UsernameStatus.CheckFailed to string(R.string.settings_profile_username_check_failed),
                UsernameStatus.Checked(UsernameCheck.ForSale) to string(R.string.settings_profile_username_for_sale),
                UsernameStatus.Checked(UsernameCheck.Invalid) to string(R.string.settings_profile_username_invalid),
                UsernameStatus.Checked(UsernameCheck.Unavailable) to
                    string(R.string.settings_profile_username_unavailable),
                UsernameStatus.Problem(UsernameProblem.TooShort) to "A username needs at least 5 characters.",
                UsernameStatus.Problem(UsernameProblem.TooLong) to "A username can have at most 32 characters.",
                UsernameStatus.Problem(UsernameProblem.InvalidCharacter) to
                    string(R.string.settings_profile_username_invalid_character),
                UsernameStatus.Problem(UsernameProblem.StartsWithNonLetter) to
                    string(R.string.settings_profile_username_starts_with_non_letter),
                UsernameStatus.Problem(UsernameProblem.EndsWithUnderscore) to
                    string(R.string.settings_profile_username_ends_with_underscore),
                UsernameStatus.Problem(UsernameProblem.ConsecutiveUnderscores) to
                    string(R.string.settings_profile_username_consecutive_underscores),
            )
        var state by mutableStateOf<EditProfileUiState>(SettingsPreviewData.editProfile)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { EditProfileScreen(state = state, onEvent = {}, onPickPhoto = {}) }
        }

        states.forEach { (status, text) ->
            state = SettingsPreviewData.editedProfile(status)
            composeRule.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun `back from the top bar asks the view model`() {
        setScreen()

        composeRule.onNodeWithContentDescription(string(R.string.settings_back)).performClick()

        assertEquals(listOf(EditProfileEvent.Back), events)
    }

    @Test
    fun `system back with unsaved changes asks the view model`() {
        setScreen(SettingsPreviewData.editedProfile(UsernameStatus.Checked(UsernameCheck.Available)))

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf(EditProfileEvent.Back), events)
    }

    @Test
    fun `the discard confirmation discards or keeps editing`() {
        setScreen(
            SettingsPreviewData
                .editedProfile(UsernameStatus.Checked(UsernameCheck.Available))
                .copy(dialog = EditProfileDialog.Discard),
        )

        composeRule.onNodeWithText(string(R.string.settings_discard_profile_dialog_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_dialog_dismiss)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_discard_profile_dialog_confirm)).performClick()

        assertEquals(listOf(EditProfileEvent.DismissDialog, EditProfileEvent.ConfirmDialog), events)
    }

    @Test
    fun `set new photo opens the photo picker`() {
        setScreen()

        composeRule.onNodeWithText(string(UiR.string.photo_editor_set)).performClick()

        assertEquals(1, photoPickerOpened)
    }

    @Test
    fun `without a photo there is nothing to remove`() {
        setScreen()

        composeRule.onNodeWithText(string(UiR.string.photo_editor_remove)).assertDoesNotExist()
    }

    @Test
    fun `remove photo asks for confirmation first`() {
        setScreen(SettingsPreviewData.editProfileWithPhoto)

        composeRule.onNodeWithText(string(UiR.string.photo_editor_remove)).performClick()

        assertEquals(listOf(EditProfileEvent.RemovePhoto), events)
    }

    @Test
    fun `the remove photo confirmation removes it`() {
        setScreen(SettingsPreviewData.editProfileWithPhoto.copy(dialog = EditProfileDialog.RemovePhoto))

        composeRule.onNodeWithText(string(R.string.settings_remove_photo_dialog_title)).assertExists()
        composeRule
            .onNode(hasText(string(R.string.settings_remove_photo_dialog_confirm)) and hasAnyAncestor(isDialog()))
            .performClick()

        assertEquals(listOf(EditProfileEvent.ConfirmDialog), events)
    }

    @Test
    fun `while the photo uploads, its buttons wait`() {
        setScreen(SettingsPreviewData.editProfileWithPhoto.copy(photoTask = PhotoTask.Uploading(progress = 0.4f)))

        composeRule.onNode(hasContentDescription(string(UiR.string.photo_editor_uploading))).assertExists()
        composeRule.onNodeWithText(string(UiR.string.photo_editor_set)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(UiR.string.photo_editor_remove)).assertIsNotEnabled()
    }

    @Test
    fun `the remove username confirmation names it`() {
        val edited = SettingsPreviewData.editedProfile(UsernameStatus.Removed, username = "")
        setScreen(edited.copy(dialog = EditProfileDialog.RemoveUsername))

        composeRule
            .onNodeWithText(string(R.string.settings_remove_username_dialog_text, "ada".bidiIsolated()).bidiIsolated())
            .assertExists()
    }

    /** This state with the text [event] typed, as the view model keeps it. */
    private fun EditProfileUiState.Content.typed(event: EditProfileEvent) =
        when (event) {
            is EditProfileEvent.FirstNameChanged -> copy(fields = fields.copy(firstName = event.value))
            is EditProfileEvent.LastNameChanged -> copy(fields = fields.copy(lastName = event.value))
            is EditProfileEvent.UsernameChanged -> copy(fields = fields.copy(username = event.value))
            is EditProfileEvent.BioChanged -> copy(fields = fields.copy(bio = event.value))
            else -> this
        }

    @Test
    fun `an error offers to try again`() {
        setScreen(EditProfileUiState.Error(DomainError.Network))

        composeRule.onNodeWithText(string(R.string.settings_retry)).performClick()

        assertEquals(listOf(EditProfileEvent.Retry), events)
    }
}
