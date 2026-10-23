package dev.kyralabs.pingogram.feature.chat.ui.translation

import android.content.res.Resources
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.TranslationEffect
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * The state with each bubble of [translations] showing its translation in place of its text, its meta shown for the
 * label; the items of every other bubble stay as they are, so only the bubbles that change compose again.
 */
internal fun ChatUiState.withTranslations(translations: ImmutableMap<Long, BubbleTranslation>): ChatUiState {
    val content = content as? ChatContent.Content
    if (translations.isEmpty() || content == null) return this
    var changed = false
    val items =
        content.items.map { item ->
            val translation = (item as? ChatItem.Bubble)?.let { translations[it.id] }
            if (item !is ChatItem.Bubble || translation == null) {
                item
            } else {
                changed = true
                val text = (translation as? BubbleTranslation.Shown)?.text ?: item.text
                item.copy(text = text, translation = translation, showsTime = true)
            }
        }
    return if (changed) copy(content = content.copy(items = items.toImmutableList())) else this
}

/**
 * "Translated from Spanish", or "Translated" without a language; "Translating…" while it is on its way. The language
 * is named in [locale], the screen's.
 */
internal fun translationLabel(
    resources: Resources,
    translation: BubbleTranslation,
    locale: Locale,
): String =
    when (translation) {
        BubbleTranslation.Translating -> {
            resources.getString(R.string.translation_translating)
        }

        is BubbleTranslation.Shown -> {
            translation.fromLanguage?.let { resources.getString(R.string.translation_from, languageName(it, locale)) }
                ?: resources.getString(R.string.translation_translated)
        }
    }

/** The name of language [languageTag] in [locale] ("Spanish", "Brazilian Portuguese" stays "Portuguese"). */
internal fun languageName(
    languageTag: String,
    locale: Locale,
): String =
    Locale
        .forLanguageTag(languageTag)
        .getDisplayLanguage(locale)
        .ifEmpty { languageTag }
        .replaceFirstChar { it.titlecase(locale) }

/** The snackbar of an error ([ChatEffect.ShowError]) or of message translation ([translationNotice]). */
internal fun failureNotice(
    resources: Resources,
    effect: ChatEffect,
    showErrorDetails: Boolean,
): String =
    when (effect) {
        is TranslationEffect -> translationNotice(resources, effect, showErrorDetails)
        is ChatEffect.ShowError -> errorMessage(resources, effect.error, showErrorDetails)
        else -> ""
    }

/**
 * The snackbar of [effect]: why Telegram did not translate (a flood wait's time, no network, Telegram Premium), that
 * translation failed where Telegram gave no reason (TDLib's own text in debug builds, [showDetails]), or that the bar
 * was closed.
 */
internal fun translationNotice(
    resources: Resources,
    effect: TranslationEffect,
    showDetails: Boolean,
): String =
    when (effect) {
        is TranslationEffect.Failed -> {
            if (effect.error is DomainError.Unknown && !showDetails) {
                resources.getString(R.string.translation_failed)
            } else {
                errorMessage(resources, effect.error, showDetails)
            }
        }

        TranslationEffect.BarHidden -> {
            resources.getString(R.string.translation_bar_hidden)
        }
    }
