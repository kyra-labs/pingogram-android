package dev.kyralabs.pingogram.feature.chat.ui.panel

import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.repository.StickerRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSuggestions
import dev.kyralabs.pingogram.feature.chat.ui.isEmojiOnly
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Stickers to suggest above the composer of chat [chatId], a part of [EmojiPanelViewModel]: while its text is one emoji
 * alone ([onTextChanged]), that emoji's stickers ([StickerRepository.stickersForEmoji]) from the user's sets or from
 * every set, as Settings → Stickers → "Suggest stickers by emoji" asks ([StickerSettingsRepository]); none while it is
 * off. Asked [ASK_DELAY_MILLIS] after the text settles, the newest text cancelling an older request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StickerSuggestionState(
    private val chatId: Long,
    private val repository: StickerRepository,
    settings: StickerSettingsRepository,
) {
    private val emoji = MutableStateFlow<String?>(null)

    /** The stickers to suggest now; empty for none. */
    val stickers: Flow<ImmutableList<Sticker>> =
        combine(emoji, settings.settings.map { it.suggestions }.distinctUntilChanged(), ::Pair)
            .flatMapLatest { (emoji, mode) ->
                if (emoji == null || mode == StickerSuggestions.Off) flowOf(NONE) else asked(emoji, mode)
            }

    /** The composer's text changed to [text]. */
    fun onTextChanged(text: String) {
        emoji.value = text.trim().takeIf { isEmojiOnly(it, max = 1) }
    }

    private fun asked(
        emoji: String,
        mode: StickerSuggestions,
    ): Flow<ImmutableList<Sticker>> =
        flow {
            emit(NONE)
            delay(ASK_DELAY_MILLIS)
            val allSets = mode == StickerSuggestions.AllSets
            emit(
                repository
                    .stickersForEmoji(emoji, chatId, allSets)
                    .getOrNull()
                    .orEmpty()
                    .toImmutableList(),
            )
        }

    private companion object {
        /** A short pause, so typing past an emoji asks nothing. */
        const val ASK_DELAY_MILLIS = 150L
        val NONE: ImmutableList<Sticker> = persistentListOf()
    }
}
