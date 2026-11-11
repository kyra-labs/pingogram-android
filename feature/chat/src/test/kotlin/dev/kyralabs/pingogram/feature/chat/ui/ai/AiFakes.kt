package dev.kyralabs.pingogram.feature.chat.ui.ai

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FixedText
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextCompositionStyle
import dev.kyralabs.pingogram.core.domain.repository.AiEditorConsentRepository
import dev.kyralabs.pingogram.core.domain.repository.AiTextRepository
import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A request made of [FakeAiText]. */
data class AiRequest(
    val text: String,
    val kind: String,
    val styleName: String? = null,
    val translateTo: String? = null,
    val addEmoji: Boolean = false,
)

/** [AiTextRepository] in memory: records every request in [requests], answering with [answer]. */
class FakeAiText : AiTextRepository {
    override val styles =
        MutableStateFlow<ImmutableList<TextCompositionStyle>>(
            persistentListOf(TextCompositionStyle("formal", "Formal"), TextCompositionStyle("short", "Short")),
        )

    override val languages: ImmutableList<String> = persistentListOf("de", "en", "es")

    val requests = mutableListOf<AiRequest>()

    /** What the next request answers; a deferred keeps it waiting. */
    var answer: CompletableDeferred<Outcome<FormattedText>> = CompletableDeferred(Outcome.Success(text("Done.")))

    var found: Outcome<TextCompositionStyle> =
        Outcome.Success(
            TextCompositionStyle("pirate", "Pirate", isCustom = true),
        )
    var limit: Int? = 3
    var addResult: Outcome<Unit> = Outcome.Success(Unit)
    val added = mutableListOf<String>()
    val removed = mutableListOf<String>()

    override suspend fun fix(text: FormattedText): Outcome<FixedText> {
        requests += AiRequest(text.text, "fix")
        return when (val result = answer.await()) {
            is Outcome.Success -> Outcome.Success(FixedText(result.value))
            is Outcome.Failure -> result
        }
    }

    override suspend fun compose(
        text: FormattedText,
        styleName: String?,
        translateTo: String?,
        addEmoji: Boolean,
    ): Outcome<FormattedText> {
        requests += AiRequest(text.text, "compose", styleName, translateTo, addEmoji)
        return answer.await()
    }

    override suspend fun findStyle(name: String): Outcome<TextCompositionStyle> = found

    override suspend fun addStyle(name: String): Outcome<Unit> =
        addResult.also {
            if (it is Outcome.Success) {
                added += name
                styles.value =
                    persistentListOf(*styles.value.toTypedArray(), TextCompositionStyle(name, name, isCustom = true))
            }
        }

    override suspend fun removeStyle(name: String): Outcome<Unit> {
        removed += name
        styles.value = persistentListOf(*styles.value.filter { it.name != name }.toTypedArray())
        return Outcome.Success(Unit)
    }

    override suspend fun addedStyleLimit(): Int? = limit

    companion object {
        fun text(text: String) = FormattedText(text, persistentListOf())

        val LIMIT_REACHED: Outcome<FormattedText> = Outcome.Failure(DomainError.AiEditLimitReached)
    }
}

class FakeAiConsent(
    read: Boolean = true,
) : AiEditorConsentRepository {
    override val acknowledged = MutableStateFlow(read)

    override suspend fun acknowledge(): Outcome<Unit> {
        acknowledged.value = true
        return Outcome.Success(Unit)
    }
}

class FakeAppLanguage(
    override val language: Flow<String?> = MutableStateFlow("de"),
) : AppLanguageRepository {
    override suspend fun supportedLanguages(): List<String> = listOf("en", "de")

    override suspend fun setLanguage(languageTag: String?): Outcome<Unit> = Outcome.Success(Unit)
}
