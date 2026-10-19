package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Edit profile page in each of its states, light and dark: loading, the profile, what it says about usernames,
 * saving, an error and the discard confirmation; the profile also on an expanded window and at 200 % font scale, and
 * a photo uploading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class EditProfileScreenScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light, SettingsPreviewData.editProfileWithPhoto)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark, SettingsPreviewData.editProfileWithPhoto)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture("expandedLight", ThemeMode.Light, SettingsPreviewData.editProfileWithPhoto)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture("fontScale200", ThemeMode.Light, SettingsPreviewData.editProfileWithPhoto)

    @Test
    fun usernameAvailableLight() = capture("usernameAvailableLight", ThemeMode.Light, available)

    @Test
    fun usernameAvailableDark() = capture("usernameAvailableDark", ThemeMode.Dark, available)

    @Test
    fun usernameTakenLight() = capture("usernameTakenLight", ThemeMode.Light, taken)

    @Test
    fun usernameTakenDark() = capture("usernameTakenDark", ThemeMode.Dark, taken)

    @Test
    fun usernameProblemLight() = capture("usernameProblemLight", ThemeMode.Light, tooShort)

    @Test
    fun usernameProblemDark() = capture("usernameProblemDark", ThemeMode.Dark, tooShort)

    @Test
    fun savingLight() = capture("savingLight", ThemeMode.Light, saving)

    @Test
    fun savingDark() = capture("savingDark", ThemeMode.Dark, saving)

    @Test
    fun uploadingLight() =
        capture(
            "uploadingLight",
            ThemeMode.Light,
            SettingsPreviewData.editProfileWithPhoto.copy(photoTask = PhotoTask.Uploading(progress = 0.6f)),
        )

    @Test
    fun discardDialogLight() = capture("discardDialogLight", ThemeMode.Light, discardDialog)

    @Test
    fun discardDialogDark() = capture("discardDialogDark", ThemeMode.Dark, discardDialog)

    @Test
    fun loadingLight() = capture("loadingLight", ThemeMode.Light, EditProfileUiState.Loading)

    @Test
    fun loadingDark() = capture("loadingDark", ThemeMode.Dark, EditProfileUiState.Loading)

    @Test
    fun errorLight() = capture("errorLight", ThemeMode.Light, EditProfileUiState.Error(DomainError.Network))

    @Test
    fun errorDark() = capture("errorDark", ThemeMode.Dark, EditProfileUiState.Error(DomainError.FloodWait(300)))

    private val available = SettingsPreviewData.editedProfile(UsernameStatus.Checked(UsernameCheck.Available))
    private val taken = SettingsPreviewData.editedProfile(UsernameStatus.Checked(UsernameCheck.Taken))
    private val tooShort =
        SettingsPreviewData.editedProfile(UsernameStatus.Problem(UsernameProblem.TooShort), username = "ada_")
    private val saving = available.copy(isSaving = true)
    private val discardDialog = available.copy(dialog = EditProfileDialog.Discard)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        state: EditProfileUiState,
    ) = composeRule.captureScreen(javaClass, name, themeMode) {
        EditProfileScreen(state = state, onEvent = {}, onPickPhoto = {})
    }
}
