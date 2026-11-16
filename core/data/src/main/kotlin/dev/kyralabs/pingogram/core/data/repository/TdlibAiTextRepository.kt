package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toFixedText
import dev.kyralabs.pingogram.core.data.mapper.toFormattedText
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.data.mapper.toTextCompositionStyle
import dev.kyralabs.pingogram.core.domain.model.FixedText
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextCompositionStyle
import dev.kyralabs.pingogram.core.domain.repository.AiTextRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [AiTextRepository] on TDLib: a [TdlibUpdateCache] of `updateTextCompositionStyles`, which TDLib announces once per
 * start and on each change; every other call is one request, made only on the user's tap.
 */
@AccountScoped
class TdlibAiTextRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : AiTextRepository,
        TdlibUpdateCache {
        private val store = UpdateStore(client, scope, persistentListOf<TextCompositionStyle>(), ::reduce)

        override val styles: StateFlow<ImmutableList<TextCompositionStyle>> = store.state

        override val languages: ImmutableList<String> =
            (TELEGRAM_LANGUAGES + CHINESE_LANGUAGES)
                .sorted()
                .toImmutableList()

        override suspend fun fix(text: FormattedText): Outcome<FixedText> =
            client.request(TdApi.FixTextWithAi(text.toTdFormattedText())).map { it.toFixedText() }

        override suspend fun compose(
            text: FormattedText,
            styleName: String?,
            translateTo: String?,
            addEmoji: Boolean,
        ): Outcome<FormattedText> =
            client
                .request(
                    TdApi.ComposeTextWithAi(
                        text.toTdFormattedText(),
                        translateTo.orEmpty(),
                        styleName.orEmpty(),
                        addEmoji,
                    ),
                ).map { it.toFormattedText() }

        override suspend fun findStyle(name: String): Outcome<TextCompositionStyle> =
            client.request(TdApi.SearchTextCompositionStyle(name)).map { it.toTextCompositionStyle() }

        override suspend fun addStyle(name: String): Outcome<Unit> =
            client.request(TdApi.AddTextCompositionStyle(name)).map {}

        override suspend fun removeStyle(name: String): Outcome<Unit> =
            client.request(TdApi.RemoveTextCompositionStyle(name)).map {}

        override suspend fun addedStyleLimit(): Int? =
            when (val read = client.request(TdApi.GetOption(STYLE_LIMIT_OPTION))) {
                is Outcome.Success -> (read.value as? TdApi.OptionValueInteger)?.value?.toInt()
                is Outcome.Failure -> null
            }

        private companion object {
            const val STYLE_LIMIT_OPTION = "added_text_composition_style_count_max"
        }
    }

/** The styles [update] announces, in its order; the same list for other updates. */
private fun reduce(
    styles: ImmutableList<TextCompositionStyle>,
    update: TdApi.Update,
): ImmutableList<TextCompositionStyle> =
    if (update is TdApi.UpdateTextCompositionStyles) {
        update.styles.map { it.toTextCompositionStyle() }.toImmutableList()
    } else {
        styles
    }
