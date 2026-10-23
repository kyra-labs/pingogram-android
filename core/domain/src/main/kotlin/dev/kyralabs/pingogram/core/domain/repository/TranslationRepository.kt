package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText

/**
 * Translating messages through Telegram (`REQUIREMENTS.md` FR-C3 "translate"), the only service that ever sees their
 * text (`REQUIREMENTS.md` §6.2). Telegram translates single messages for everyone, keeping the formatting for Telegram
 * Premium subscribers only, and suggests translating whole chats to Premium subscribers (`Chat.isTranslatable`).
 */
interface TranslationRepository {
    /**
     * The code Telegram translates into for [languageTag] (IETF BCP 47, "pt-BR", "zh-Hant"): "pt", "zh-TW"; null for
     * a language Telegram does not translate into.
     */
    fun targetLanguageOf(languageTag: String): String?

    /**
     * The text or caption of message [messageId] of chat [chatId] in [toLanguage], a code of [targetLanguageOf]. Each
     * call is one request to Telegram, made only on the user's request. Fails with `DomainError.FloodWait` when
     * Telegram asks to wait, `DomainError.PremiumRequired` where it refuses without Telegram Premium,
     * `DomainError.Network` without an answer in time, and `DomainError.Unknown` for a message it cannot translate.
     */
    suspend fun translateMessage(
        chatId: Long,
        messageId: Long,
        toLanguage: String,
    ): Outcome<FormattedText>
}
