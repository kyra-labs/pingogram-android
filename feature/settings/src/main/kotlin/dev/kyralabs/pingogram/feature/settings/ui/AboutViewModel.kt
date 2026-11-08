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
 * @property showsCheckForUpdates whether "Check for updates" shows: not when an app store installed Pingogram
 *   ([AppInfo.isFromAppStore]), as the store updates it and GitHub's releases would be another build.
 */
@Immutable
data class AboutUiState(
    val versionName: String,
    val showsCheckForUpdates: Boolean = true,
)

/** What the user does on the About page, handled by [AboutViewModel.onEvent]. */
sealed interface AboutEvent {
    /** The source code row was tapped. */
    data object OpenSourceCode : AboutEvent

    /** The privacy policy row was tapped. */
    data object OpenPrivacyPolicy : AboutEvent

    /** "What's new": this version's release notes on GitHub, in the browser. */
    data object OpenReleaseNotes : AboutEvent

    /** "Check for updates": the latest release on GitHub, in the browser; the app asks no server itself. */
    data object OpenLatestRelease : AboutEvent
}

/** One-off side effects of [AboutViewModel], handled once by its route. */
sealed interface AboutEffect {
    /** Opens [url] in the browser. */
    data class OpenUrl(
        val url: String,
    ) : AboutEffect
}

/** The About page: the app's version from `:app`'s [AppInfo], its source code on GitHub, and its privacy policy. */
@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        appInfo: AppInfo,
    ) : ViewModel() {
        val uiState: StateFlow<AboutUiState> =
            MutableStateFlow(
                AboutUiState(appInfo.versionName, showsCheckForUpdates = !appInfo.isFromAppStore),
            ).asStateFlow()

        /** This version's release on GitHub, tagged `v` and the version. */
        private val releaseNotesUrl = "$SOURCE_CODE_URL/releases/tag/v${appInfo.versionName}"

        private val effectChannel = Channel<AboutEffect>(Channel.BUFFERED)
        val effects: Flow<AboutEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: AboutEvent) {
            when (event) {
                AboutEvent.OpenSourceCode -> effectChannel.trySend(AboutEffect.OpenUrl(SOURCE_CODE_URL))
                AboutEvent.OpenPrivacyPolicy -> effectChannel.trySend(AboutEffect.OpenUrl(PRIVACY_POLICY_URL))
                AboutEvent.OpenReleaseNotes -> effectChannel.trySend(AboutEffect.OpenUrl(releaseNotesUrl))
                AboutEvent.OpenLatestRelease -> effectChannel.trySend(AboutEffect.OpenUrl(LATEST_RELEASE_URL))
            }
        }

        internal companion object {
            /** The repository Pingogram is developed and released in (`REQUIREMENTS.md` §2). */
            const val SOURCE_CODE_URL = "https://github.com/kyra-labs/pingogram-android"

            /**
             * Pingogram's own privacy policy, the website's page made from `PRIVACY.md` (`docs/PLAY_STORE.md` §8: the
             * same URL as Play Console's). Not Telegram's, which the Welcome screen links as what signing in accepts.
             */
            const val PRIVACY_POLICY_URL = "https://pingogram.kyralabs.dev/privacy/"

            /** The newest release on GitHub, where the APKs are (`docs/RELEASE.md`). */
            const val LATEST_RELEASE_URL = "$SOURCE_CODE_URL/releases/latest"
        }
    }
