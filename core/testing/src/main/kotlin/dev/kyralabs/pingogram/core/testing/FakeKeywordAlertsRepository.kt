package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.keyword.KeywordMatcher
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import dev.kyralabs.pingogram.core.domain.repository.KeywordAlertsRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

/** [KeywordAlertsRepository] in memory, starting with [initial]; [failure] makes the changes fail. */
class FakeKeywordAlertsRepository(
    initial: KeywordAlertSettings = KeywordAlertSettings(),
) : KeywordAlertsRepository {
    override val settings = MutableStateFlow(initial)

    /** What the changes fail with; null saves. */
    var failure: DomainError? = null

    override suspend fun addWord(word: String): Outcome<Unit> =
        change {
            val trimmed = word.trim()
            val known = it.words.any { kept -> KeywordMatcher.keyOf(kept) == KeywordMatcher.keyOf(trimmed) }
            if (trimmed.isEmpty() || known || it.words.size >= KeywordAlertSettings.MAX_WORDS) {
                it
            } else {
                it.copy(words = (it.words + trimmed).toImmutableList())
            }
        }

    override suspend fun removeWord(word: String): Outcome<Unit> =
        change { it.copy(words = (it.words - word).toImmutableList()) }

    override suspend fun setWhere(where: KeywordScope): Outcome<Unit> = change { it.copy(where = where) }

    override suspend fun setAlertWhenMuted(alert: Boolean): Outcome<Unit> = change { it.copy(alertWhenMuted = alert) }

    override suspend fun setShowInChatList(show: Boolean): Outcome<Unit> = change { it.copy(showInChatList = show) }

    private fun change(update: (KeywordAlertSettings) -> KeywordAlertSettings): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        settings.value = update(settings.value)
        return Outcome.Success(Unit)
    }
}
