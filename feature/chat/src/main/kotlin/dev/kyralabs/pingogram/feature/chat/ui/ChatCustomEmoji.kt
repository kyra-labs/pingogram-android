package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshotFlow
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.repository.CustomEmojiRepository
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.textOrCaption
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The custom emoji of a conversation (`docs/ARCHITECTURE.md`, Custom emoji), for [ChatViewModel]: the ids of those in
 * the texts, captions and reactions of the messages on screen ([setVisible]) and in the composer's text ([start]: a
 * draft, a message being edited) go to the [CustomEmojiRepository], which loads those it does not have; never the
 * whole history's. While the list scrolls, the ids then on screen go at most every [BATCH_WINDOW], so a fling makes a
 * few requests, not one per message. A failed request leaves the fallback emoji; the ids are asked for again when
 * they show again. The screen draws what [customEmoji] holds, kept out of `ChatUiState`, so a newly loaded emoji
 * recomposes no bubble.
 */
internal class ChatCustomEmoji(
    private val repository: CustomEmojiRepository,
    private val scope: CoroutineScope,
) {
    private val visible = MutableStateFlow<Set<Long>>(emptySet())
    private val composing = MutableStateFlow<Set<Long>>(emptySet())

    /** Every custom emoji loaded so far, by id. */
    val customEmoji: StateFlow<ImmutableMap<Long, CustomEmoji>> = repository.customEmoji

    /** Starts loading what shows, and following the composer's [text], until the scope ends. */
    fun start(text: ComposerText) {
        scope.launch {
            snapshotFlow { text.content.customEmojiIds() }.collect { composing.value = it }
        }
        scope.launch {
            combine(visible, composing) { onScreen, composer -> onScreen + composer }
                .distinctUntilChanged()
                .conflate()
                .collect { ids ->
                    if (ids.isNotEmpty()) {
                        launch { repository.load(ids) }
                        delay(BATCH_WINDOW)
                    }
                }
        }
    }

    /** The messages on screen now. */
    fun setVisible(messages: List<Message>) {
        visible.value = messages.flatMapTo(HashSet()) { it.customEmojiIds() }
    }

    private companion object {
        /** How often the ids on screen go to the repository at most. */
        val BATCH_WINDOW = 100.milliseconds
    }
}

/** The custom emoji of this message's text or caption and of its reactions. */
private fun Message.customEmojiIds(): Set<Long> {
    val reactions =
        interactionInfo
            ?.reactions
            .orEmpty()
            .mapNotNull { (it.type as? ReactionType.CustomEmoji)?.customEmojiId }
    return textOrCaption.customEmojiIds() + reactions
}

/** The custom emoji of this text. */
private fun FormattedText.customEmojiIds(): Set<Long> =
    entities.mapNotNullTo(HashSet()) { (it.type as? TextEntityType.CustomEmoji)?.customEmojiId }
