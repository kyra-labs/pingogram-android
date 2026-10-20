package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [AppLanguageRepository] in memory: [supported] languages, following the system's at first unless [initial];
 * [failure] makes saving fail.
 */
class FakeAppLanguageRepository(
    var supported: List<String> = listOf("en"),
    initial: String? = null,
) : AppLanguageRepository {
    override val language = MutableStateFlow(initial)

    /** What saving fails with; null saves. */
    var failure: DomainError? = null

    override suspend fun supportedLanguages(): List<String> = supported

    override suspend fun setLanguage(languageTag: String?): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        language.value = languageTag
        return Outcome.Success(Unit)
    }
}
