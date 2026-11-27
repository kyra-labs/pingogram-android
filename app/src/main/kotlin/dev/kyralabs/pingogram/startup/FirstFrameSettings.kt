package dev.kyralabs.pingogram.startup

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.CleanLinksRepository
import dev.kyralabs.pingogram.core.domain.repository.IncognitoKeyboardRepository
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberVisibilityRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyScreenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The saved settings `MainActivity` waits for before its first frame, read once per process and kept, then every
 * change: null until read. Each activity start finds them in memory instead of reading the settings file again
 * (`main.awaitSettings`, `docs/PERFORMANCE.md` §2); `PingogramApplication` starts the read.
 */
@Singleton
class FirstFrameSettings
    @Inject
    constructor(
        appearanceRepository: AppearanceRepository,
        phoneNumberVisibility: PhoneNumberVisibilityRepository,
        incognitoKeyboard: IncognitoKeyboardRepository,
        cleanLinks: CleanLinksRepository,
        privacyScreen: PrivacyScreenRepository,
        @ApplicationScope scope: CoroutineScope,
    ) {
        /** The Appearance settings the app's theme follows. */
        val appearance: StateFlow<AppearanceSettings?> =
            appearanceRepository.appearance.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

        /** Privacy and security's settings the whole app follows on screen. */
        val screenPrivacy: StateFlow<ScreenPrivacy?> =
            combine(
                phoneNumberVisibility.hidden,
                incognitoKeyboard.enabled,
                cleanLinks.enabled,
                privacyScreen.enabled,
                ::ScreenPrivacy,
            ).stateIn(scope, SharingStarted.Eagerly, initialValue = null)

        /**
         * Whether phone numbers are hidden on screen, whether text fields ask the keyboard not to learn, whether
         * links open without their tracking parameters, and whether the privacy screen hides messages.
         */
        data class ScreenPrivacy(
            val phoneNumbersHidden: Boolean,
            val incognitoKeyboard: Boolean,
            val cleanLinks: Boolean,
            val privacyScreen: Boolean,
        )
    }
