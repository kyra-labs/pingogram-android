package dev.kyralabs.pingogram.startup

import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakeAppearanceRepository
import dev.kyralabs.pingogram.core.testing.FakeCleanLinksRepository
import dev.kyralabs.pingogram.core.testing.FakeIncognitoKeyboardRepository
import dev.kyralabs.pingogram.core.testing.FakePhoneNumberVisibilityRepository
import dev.kyralabs.pingogram.startup.FirstFrameSettings.ScreenPrivacy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirstFrameSettingsTest {
    private val phoneNumbers = FakePhoneNumberVisibilityRepository(hidden = true)
    private val incognitoKeyboard = FakeIncognitoKeyboardRepository(enabled = true)
    private val cleanLinks = FakeCleanLinksRepository(enabled = false)

    private fun TestScope.settings(appearance: AppearanceRepository): FirstFrameSettings =
        FirstFrameSettings(appearance, phoneNumbers, incognitoKeyboard, cleanLinks, backgroundScope)

    @Test
    fun `has no settings until they are read`() =
        runTest {
            val settings = settings(FakeAppearanceRepository())

            assertNull(settings.appearance.value)
            assertNull(settings.screenPrivacy.value)
        }

    @Test
    fun `reads the settings without anyone collecting them, then follows their changes`() =
        runTest {
            val appearance = FakeAppearanceRepository(AppearanceSettings())
            val settings = settings(appearance)
            runCurrent()

            assertEquals(AppearanceSettings(), settings.appearance.value)
            assertEquals(
                ScreenPrivacy(phoneNumbersHidden = true, incognitoKeyboard = true, cleanLinks = false),
                settings.screenPrivacy.value,
            )

            appearance.save(AppearanceSettings(themeMode = ThemeMode.Dark))
            phoneNumbers.hidden.value = false
            runCurrent()

            assertEquals(AppearanceSettings(themeMode = ThemeMode.Dark), settings.appearance.value)
            assertEquals(
                ScreenPrivacy(phoneNumbersHidden = false, incognitoKeyboard = true, cleanLinks = false),
                settings.screenPrivacy.value,
            )
        }

    @Test
    fun `reads the settings file once, however many activities read them`() =
        runTest {
            val fake = FakeAppearanceRepository(AppearanceSettings())
            var reads = 0
            val counted =
                object : AppearanceRepository by fake {
                    override val appearance: Flow<AppearanceSettings> = fake.appearance.onStart { reads++ }
                }
            val settings = settings(counted)
            runCurrent()

            repeat(3) { assertEquals(AppearanceSettings(), settings.appearance.value) }

            assertEquals(1, reads)
        }
}
