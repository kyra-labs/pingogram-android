package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.AppInfo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

/**
 * What the About page shows (`REQUIREMENTS.md` FR-S1).
 *
 * @property versionName the app's version, e.g. `0.3.0`.
 */
@Immutable
data class AboutUiState(
    val versionName: String,
)

/** What the user does on the About page, handled by [AboutViewModel.onEvent]. */
sealed interface AboutEvent {
    /** The source code row was tapped. */
    data object OpenSourceCode : AboutEvent
}

/** One-off side effects of [AboutViewModel], handled once by its route. */
sealed interface AboutEffect {
    /** Opens [url] in the browser. */
    data class OpenUrl(
        val url: String,
    ) : AboutEffect
}

/** The About page: the app's version from `:app`'s [AppInfo], and its source code on GitHub. */
@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        appInfo: AppInfo,
    ) : ViewModel() {
        val uiState: StateFlow<AboutUiState> = MutableStateFlow(AboutUiState(appInfo.versionName)).asStateFlow()

        private val effectChannel = Channel<AboutEffect>(Channel.BUFFERED)
        val effects: Flow<AboutEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: AboutEvent) {
            when (event) {
                AboutEvent.OpenSourceCode -> effectChannel.trySend(AboutEffect.OpenUrl(SOURCE_CODE_URL))
            }
        }

        internal companion object {
            /** The repository Pingogram is developed and released in (`REQUIREMENTS.md` §2). */
            const val SOURCE_CODE_URL = "https://github.com/kyra-labs/pingogram-android"
        }
    }
