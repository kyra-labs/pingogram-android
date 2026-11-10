package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.StoryPostAbility
import dev.kyralabs.pingogram.core.domain.model.StoryPrivacy
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.repository.StoryPostRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who a story is for, as the composer's chip offers it. */
internal enum class StoryAudience(
    val privacy: StoryPrivacy,
) {
    Everyone(StoryPrivacy.Everyone),
    Contacts(StoryPrivacy.Contacts),
    CloseFriends(StoryPrivacy.CloseFriends),
}

/**
 * The composer's state (board 10b): who the story is for, whether Telegram lets the user post now ([ability], null
 * while asked), how many close friends there are (null until known) and whether the stories are being handed to TDLib.
 */
@Immutable
internal data class StoryComposeState(
    val audience: StoryAudience = StoryAudience.Everyone,
    val ability: StoryPostAbility? = null,
    val closeFriends: Int? = null,
    val isPosting: Boolean = false,
) {
    val canPost: Boolean get() = ability is StoryPostAbility.Allowed && !isPosting
}

/**
 * Composing a new story: [caption] with its formatting and who it is for, kept in [savedState]; [start] asks Telegram
 * once whether the user may post and how many close friends they have, and [post] hands the items to
 * [StoryPostRepository] in [applicationScope], so they go on when the screen closes at once. A part of
 * [NewStoryViewModel], which runs it in [scope].
 */
internal class StoryComposer(
    private val savedState: SavedStateHandle,
    private val repository: StoryPostRepository,
    private val scope: CoroutineScope,
    private val applicationScope: CoroutineScope,
) {
    val caption = CaptionText(savedState.restoredCaption())

    private val mutableState = MutableStateFlow(StoryComposeState(audience = savedState.restoredAudience()))
    val state: StateFlow<StoryComposeState> = mutableState.asStateFlow()

    private var started = false

    /** Keeps the caption in [savedState] as it changes, and asks Telegram what the composer needs; once. */
    fun start() {
        if (started) return
        started = true
        scope.launch { snapshotFlow { caption.content }.collect { savedState.saveCaption(it) } }
        scope.launch {
            val ability = (repository.ability() as? Outcome.Success)?.value ?: StoryPostAbility.NotAllowed
            mutableState.update { it.copy(ability = ability) }
        }
        scope.launch {
            val count = (repository.closeFriendCount() as? Outcome.Success)?.value
            mutableState.update { it.copy(closeFriends = count) }
        }
    }

    fun setAudience(audience: StoryAudience) {
        savedState[KEY_AUDIENCE] = audience.name
        mutableState.update { it.copy(audience = audience) }
    }

    /**
     * Posts [items] with the caption to the chosen audience; [onPosted] once TDLib took them all, [onFailed] with
     * what went wrong otherwise. Does nothing while posting or when posting is not allowed.
     */
    fun post(
        items: List<Attachment>,
        onPosted: () -> Unit,
        onFailed: (DomainError) -> Unit,
    ) {
        val current = mutableState.value
        if (!current.canPost || items.isEmpty()) return
        mutableState.update { it.copy(isPosting = true) }
        val content = caption.content.trimmed()
        applicationScope.launch {
            val result = repository.post(items, content, current.audience.privacy)
            mutableState.update { it.copy(isPosting = false) }
            when (result) {
                is Outcome.Success -> onPosted()
                is Outcome.Failure -> onFailed(result.error)
            }
        }
    }
}

/** The caption without white space at its ends, its formatting kept where it is. */
private fun FormattedText.trimmed(): FormattedText {
    val start = text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return FormattedText.Empty
    val end = text.indexOfLast { !it.isWhitespace() } + 1
    val entities =
        entities.mapNotNull { entity ->
            val from = maxOf(entity.offset, start)
            val to = minOf(entity.offset + entity.length, end)
            if (to > from) TextEntity(from - start, to - from, entity.type) else null
        }
    return FormattedText(text.substring(start, end), entities.toImmutableList())
}

// The caption in a SavedStateHandle: its text, and each entity the formatting bar sets as "offset|length|kind" or, for
// a link, "offset|length|link|address".

private const val KEY_CAPTION = "newStory.caption"
private const val KEY_CAPTION_ENTITIES = "newStory.captionEntities"
private const val KEY_AUDIENCE = "newStory.audience"
private const val SEPARATOR = "|"
private const val LINK = "link"
private const val PARTS = 4

private val KINDS =
    mapOf(
        "bold" to TextEntityType.Bold,
        "italic" to TextEntityType.Italic,
        "spoiler" to TextEntityType.Spoiler,
    )

private fun SavedStateHandle.saveCaption(caption: FormattedText) {
    this[KEY_CAPTION] = caption.text
    this[KEY_CAPTION_ENTITIES] =
        caption.entities.mapNotNullTo(ArrayList()) { entity ->
            val type = entity.type
            val kind =
                if (type is TextEntityType.TextUrl) {
                    "$LINK$SEPARATOR${type.url}"
                } else {
                    KINDS.entries
                        .firstOrNull {
                            it.value ==
                                type
                        }?.key
                }
            kind?.let { "${entity.offset}$SEPARATOR${entity.length}$SEPARATOR$it" }
        }
}

private fun SavedStateHandle.restoredCaption(): FormattedText {
    val text = get<String>(KEY_CAPTION) ?: return FormattedText.Empty
    val entities =
        get<ArrayList<String>>(KEY_CAPTION_ENTITIES).orEmpty().mapNotNull { saved ->
            val parts = saved.split(SEPARATOR, limit = PARTS)
            val offset = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..text.length }
            val length = parts.getOrNull(1)?.toIntOrNull()?.takeIf { offset != null && it in 1..text.length - offset }
            val type =
                parts.getOrNull(2)?.let {
                    if (it ==
                        LINK
                    ) {
                        parts.getOrNull(PARTS - 1)?.let(TextEntityType::TextUrl)
                    } else {
                        KINDS[it]
                    }
                }
            if (offset != null && length != null && type != null) TextEntity(offset, length, type) else null
        }
    return FormattedText(text, entities.toImmutableList())
}

private fun SavedStateHandle.restoredAudience(): StoryAudience =
    get<String>(KEY_AUDIENCE)?.let { name -> StoryAudience.entries.firstOrNull { it.name == name } }
        ?: StoryAudience.Everyone
