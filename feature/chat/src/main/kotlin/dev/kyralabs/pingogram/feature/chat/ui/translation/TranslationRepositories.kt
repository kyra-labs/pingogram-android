package dev.kyralabs.pingogram.feature.chat.ui.translation

import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceLanguageDetector
import dev.kyralabs.pingogram.core.domain.repository.HiddenTranslateBarsRepository
import dev.kyralabs.pingogram.core.domain.repository.TranslationRepository
import javax.inject.Inject

/**
 * What the conversation's message translation ([ChatTranslation]) works with: Telegram's translation
 * ([translations]), the device's languages and its detection of a text's language ([detector]), Pingogram's own
 * language, which translations go into ([appLanguage]), and the chats whose translate bar the user closed
 * ([hiddenBars]). One parameter of `ChatViewModel` for them, which already takes one repository per kind of data it
 * combines.
 */
class TranslationRepositories
    @Inject
    constructor(
        val translations: TranslationRepository,
        val detector: DeviceLanguageDetector,
        val appLanguage: AppLanguageRepository,
        val hiddenBars: HiddenTranslateBarsRepository,
    )
