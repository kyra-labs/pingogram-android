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
}
