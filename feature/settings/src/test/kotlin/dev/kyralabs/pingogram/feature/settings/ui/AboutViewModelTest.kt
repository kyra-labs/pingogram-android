package dev.kyralabs.pingogram.feature.settings.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.AppInfo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AboutViewModelTest {
    private val viewModel = AboutViewModel(AppInfo(versionName = "1.2.3"))

    @Test
    fun `shows the app's version`() {
        assertEquals(AboutUiState(versionName = "1.2.3"), viewModel.uiState.value)
    }

    @Test
    fun `the source code row opens the GitHub repository`() =
        runTest {
            viewModel.effects.test {
                viewModel.onEvent(AboutEvent.OpenSourceCode)

                assertEquals(AboutEffect.OpenUrl("https://github.com/kyra-labs/pingogram-android"), awaitItem())
            }
        }

    @Test
    fun `the privacy policy row opens Pingogram's privacy policy`() =
        runTest {
            viewModel.effects.test {
                viewModel.onEvent(AboutEvent.OpenPrivacyPolicy)

                assertEquals(AboutEffect.OpenUrl("https://pingogram.kyralabs.dev/privacy/"), awaitItem())
            }
        }

    @Test
    fun `what's new opens this version's release and check for updates the latest one, in the browser`() =
        runTest {
            viewModel.effects.test {
                viewModel.onEvent(AboutEvent.OpenReleaseNotes)
                viewModel.onEvent(AboutEvent.OpenLatestRelease)

                assertEquals(
                    AboutEffect.OpenUrl("https://github.com/kyra-labs/pingogram-android/releases/tag/v1.2.3"),
                    awaitItem(),
                )
                assertEquals(
                    AboutEffect.OpenUrl("https://github.com/kyra-labs/pingogram-android/releases/latest"),
                    awaitItem(),
                )
            }
        }
}
