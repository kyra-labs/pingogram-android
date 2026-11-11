package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.mediaContents
import dev.kyralabs.pingogram.core.domain.model.richMediaId
import dev.kyralabs.pingogram.core.domain.model.richMediaMessageId
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.RichMessageEvent
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How a rich message's bubble shows: the sections the user opened or closed ([toggledSections], by
 * `RichBlock.Details.id`, against how each starts), whether a long message shows all of it ([showsAll]) and whether
 * its full message is loading ([isLoadingFull]).
 */
@Immutable
data class RichMessageUi(
    val toggledSections: ImmutableSet<Int> = persistentSetOf(),
    val showsAll: Boolean = false,
    val isLoadingFull: Boolean = false,
)

/** Whether [section] shows its blocks: as it starts (`isOpen`), unless the user toggled it. */
internal fun RichMessageUi?.isOpen(section: RichBlock.Details): Boolean =
    section.isOpen != (this?.toggledSections?.contains(section.id) == true)

/**
 * The rich messages of chat [chatId], for `ChatViewModel`: the sections the user opens and closes and the long
 * messages they show all of, by message, kept across process death; and the full message of those Telegram sent only
 * the start of, loaded once per tap through [messages] (the thread's repository, which puts it into the history). A
 * failure is a [ChatEffect.ShowError] snackbar. Runs in [scope], the view model's, which cancels a load with it.
 */
internal class ChatRichMessages(
    private val chatId: Long,
    private val messages: MessageRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val toggled =
        MutableStateFlow(savedState.get<ArrayList<String>>(KEY_TOGGLED)?.let(::sectionsOf) ?: persistentMapOf())

    private val showingAll =
        MutableStateFlow(savedState.get<LongArray>(KEY_SHOWING_ALL)?.toList()?.toPersistentSet() ?: persistentSetOf())

    private val loading = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** The loads of full messages under way, by message. */
    private val loads = mutableMapOf<Long, Job>()

    /** How each rich message the user changed shows, by message id; the others are as they start. */
    val state: Flow<PersistentMap<Long, RichMessageUi>> =
        combine(toggled, showingAll, loading) { sections, all, loadingIds ->
            (sections.keys + all + loadingIds)
                .associateWith { id ->
                    RichMessageUi(
                        toggledSections = sections[id] ?: persistentSetOf(),
                        showsAll = id in all,
                        isLoadingFull = id in loadingIds,
                    )
                }.toPersistentMap()
        }

    fun onEvent(event: RichMessageEvent) {
        when (event) {
            is RichMessageEvent.ToggleSection -> toggle(event.messageId, event.sectionId)
            is RichMessageEvent.ShowAll -> showAll(event.messageId)
            is RichMessageEvent.LoadFull -> loadFull(event.messageId)
        }
    }

    private fun toggle(
        messageId: Long,
        sectionId: Int,
    ) {
        toggled.update { all ->
            val sections = all[messageId].orEmpty()
            val changed = if (sectionId in sections) sections - sectionId else sections + sectionId
            if (changed.isEmpty()) all.remove(messageId) else all.put(messageId, changed.toPersistentSet())
        }
        savedState[KEY_TOGGLED] = ArrayList(toggled.value.map { (id, sections) -> "$id:${sections.joinToString(",")}" })
    }

    private fun showAll(messageId: Long) {
        showingAll.update { it.add(messageId) }
        savedState[KEY_SHOWING_ALL] = showingAll.value.toLongArray()
    }

    /** Loads the full message once; a second tap while it loads does nothing. */
    private fun loadFull(messageId: Long) {
        if (loads.containsKey(messageId)) return
        loading.update { it.add(messageId) }
        loads[messageId] =
            scope.launch {
                try {
                    val result = messages.loadFullRichMessage(chatId, messageId)
                    if (result is Outcome.Failure) onEffect(ChatEffect.ShowError(result.error))
                } finally {
                    loading.update { it.remove(messageId) }
                    loads.remove(messageId)
                }
            }
    }

    private companion object {
        const val KEY_TOGGLED = "rich_toggled_sections"
        const val KEY_SHOWING_ALL = "rich_showing_all"
    }
}

/** The toggled sections kept in the saved state: "messageId:id,id" per message. */
private fun sectionsOf(saved: List<String>): PersistentMap<Long, PersistentSet<Int>> =
    saved
        .mapNotNull { entry ->
            val messageId = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            val ids = entry.substringAfter(':').split(',').mapNotNull(String::toIntOrNull)
            if (ids.isEmpty()) null else messageId to ids.toPersistentSet()
        }.toMap()
        .toPersistentMap()

/**
 * The state with how each changed rich message shows ([ChatRichMessages.state]) copied into its bubble only, so no
 * other recomposes; the state as it is while none changed.
 */
internal fun ChatUiState.withRichMessages(states: PersistentMap<Long, RichMessageUi>): ChatUiState {
    val content = content as? ChatContent.Content
    if (states.isEmpty() || content == null) return this
    var changed = false
    val items =
        content.items.map { item ->
            val bubble = item as? ChatItem.Bubble
            val rich = bubble?.let { states[it.id] }
            if (bubble == null || rich == null) {
                item
            } else {
                changed = true
                bubble.copy(rich = rich)
            }
        }
    return if (changed) copy(content = content.copy(items = items.toImmutableList())) else this
}

/**
 * The media viewer's effect for a rich message's media [id] ([richMediaId]), or a paid post's once paid for, where a
 * bubble shows that message with that media; null otherwise.
 */
internal fun ChatUiState.richMediaToOpen(id: Long): ChatEffect.OpenMedia? {
    val messageId = richMediaMessageId(id)
    val content =
        (content as? ChatContent.Content)?.items?.firstNotNullOfOrNull { item ->
            (item as? ChatItem.Bubble)?.takeIf { it.id == messageId }?.content
        }
    val shown =
        when (content) {
            is MessageContent.RichMessage -> {
                content.message.media().any { richMediaId(messageId, it.index) == id }
            }

            is MessageContent.PaidMedia -> {
                content.mediaContents().any { (index, _) ->
                    richMediaId(messageId, index) ==
                        id
                }
            }

            else -> {
                false
            }
        }
    return if (shown) ChatEffect.OpenMedia(id) else null
}
