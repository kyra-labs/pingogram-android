package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.repository.HiddenTranslateBarsRepository
import dev.kyralabs.pingogram.core.domain.repository.TranslationRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [TranslationRepository] in memory: translates every language Telegram's codes name by the language subtag, unless
 * in [unsupported]; [onTranslate] answers each request (by default "[code] message <id>"), recorded in [requests].
 */
class FakeTranslationRepository : TranslationRepository {
    /** Every request, as (chat id, message id, language code), in order. */
    val requests = mutableListOf<Triple<Long, Long, String>>()

    var unsupported: Set<String> = emptySet()

    var onTranslate: suspend (chatId: Long, messageId: Long, code: String) -> Outcome<FormattedText> =
        { _, messageId, code -> Outcome.Success(translationOf(messageId, code)) }

    override fun targetLanguageOf(languageTag: String): String? =
        languageTag.substringBefore('-').lowercase().takeUnless { it in unsupported || it.isEmpty() }

    override suspend fun translateMessage(
        chatId: Long,
        messageId: Long,
        toLanguage: String,
    ): Outcome<FormattedText> {
        requests += Triple(chatId, messageId, toLanguage)
        return onTranslate(chatId, messageId, toLanguage)
    }

    companion object {
        /** What the fake translates message [messageId] into [code] as by default. */
        fun translationOf(
            messageId: Long,
            code: String,
        ) = FormattedText("[$code] message $messageId", persistentListOf())
    }
}

/** [HiddenTranslateBarsRepository] in memory; [onHide] answers closing a bar, by default closing it. */
class FakeHiddenTranslateBarsRepository : HiddenTranslateBarsRepository {
    val hidden = MutableStateFlow<Set<Long>>(emptySet())

    var onHide: suspend (Long) -> Outcome<Unit> = { chatId ->
        hidden.value += chatId
        Outcome.Success(Unit)
    }

    override fun isHidden(chatId: Long): Flow<Boolean> = hidden.map { chatId in it }.distinctUntilChanged()

    override suspend fun hide(chatId: Long): Outcome<Unit> = onHide(chatId)
}
