package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.keyword.KeywordMatcher
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import dev.kyralabs.pingogram.core.domain.repository.MutedWordsRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

/** [MutedWordsRepository] in memory, starting with [initial]; [failure] makes the changes fail. */
class FakeMutedWordsRepository(
    initial: MutedWordSettings = MutedWordSettings(),
) : MutedWordsRepository {
    override val settings = MutableStateFlow(initial)

    /** What the changes fail with; null saves. */
    var failure: DomainError? = null

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> = change { it.copy(enabled = enabled) }

    override suspend fun addWord(word: String): Outcome<Unit> =
        change {
            val trimmed = word.trim().take(MutedWordSettings.MAX_WORD_LENGTH)
            val known = it.words.any { kept -> KeywordMatcher.keyOf(kept) == KeywordMatcher.keyOf(trimmed) }
            if (trimmed.isEmpty() || known || it.words.size >= MutedWordSettings.MAX_WORDS) {
                it
            } else {
                it.copy(words = (it.words + trimmed).toImmutableList())
            }
        }

    override suspend fun removeWord(word: String): Outcome<Unit> =
        change { it.copy(words = (it.words - word).toImmutableList()) }

    private fun change(update: (MutedWordSettings) -> MutedWordSettings): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        settings.value = update(settings.value)
        return Outcome.Success(Unit)
    }
}
