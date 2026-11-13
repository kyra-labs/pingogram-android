package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toFormattedText
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.primaryLanguageOf
import dev.kyralabs.pingogram.core.domain.repository.TranslationRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import java.util.Locale
import javax.inject.Inject

/**
 * [TranslationRepository] on TDLib's `translateMessageText`, which has Telegram translate a message it knows by its id,
 * so the text goes nowhere else (`REQUIREMENTS.md` §6.2). No cache: the conversation keeps what it translated while it
 * lives (`ChatTranslation`), and TDLib asks Telegram again for each request. Telegram translates for every user and
 * keeps the formatting for Telegram Premium subscribers only; whatever it refuses is the caller's error to show, never
 * worked around. Summaries (`summarizeMessage`) go the same way: Telegram summarizes the message it knows by its id.
 */
@AccountScoped
class TdlibTranslationRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : TranslationRepository {
        override fun targetLanguageOf(languageTag: String): String? {
            val locale = Locale.forLanguageTag(languageTag)
            return when (val language = primaryLanguageOf(languageTag)) {
                // Telegram's two Chinese: traditional where the tag says so, by script or by region, else simplified.
                "zh" -> if (locale.script == "Hant" || locale.country in TRADITIONAL_REGIONS) "zh-TW" else "zh-CN"

                "nb", "nn" -> "no"

                "fil" -> "tl"

                else -> language.takeIf { it in TELEGRAM_LANGUAGES }
            }
        }

        override suspend fun translateMessage(
            chatId: Long,
            messageId: Long,
            toLanguage: String,
        ): Outcome<FormattedText> =
            client
                .request(TdApi.TranslateMessageText(chatId, messageId, toLanguage, DEFAULT_TONE))
                .map { it.toFormattedText() }

        override suspend fun summarizeMessage(
            chatId: Long,
            messageId: Long,
            toLanguage: String?,
        ): Outcome<FormattedText> =
            client
                .request(TdApi.SummarizeMessage(chatId, messageId, toLanguage.orEmpty(), DEFAULT_TONE))
                .map { it.toFormattedText() }

        private companion object {
            /** td_api.tl `translateText.tone`: the empty string is Telegram's own choice. */
            const val DEFAULT_TONE = ""

            val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")
        }
    }
