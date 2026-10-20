package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The app's own language (`REQUIREMENTS.md` FR-S1 "Language", §6.4), apart from the system's: Android keeps it from
 * Android 13 on (the per-app language API, also in Android's settings), Pingogram's settings before. Languages are
 * IETF BCP 47 tags ("en", "pt-BR"); null stands for the system's language.
 */
interface AppLanguageRepository {
    /** The languages Pingogram ships, in the order Settings lists them. */
    suspend fun supportedLanguages(): List<String>

    /**
     * The language chosen for the app, or null while it follows the system's, then every change made in Pingogram.
     * Never fails: a choice that cannot be read counts as the system's language. From Android 13 on Android's settings
     * can change it too; each new collection reads it again.
     */
    val language: Flow<String?>

    /**
     * Makes [languageTag] the app's language, or the system's with null; the screens show it once they are created
     * again, which Android (13+) or the app does at once. Fails with `DomainError.Unknown` if it cannot be saved.
     */
    suspend fun setLanguage(languageTag: String?): Outcome<Unit>
}
