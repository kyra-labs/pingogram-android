package dev.kyralabs.pingogram.feature.chat.ui.pinned

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.repository.HiddenPinsRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatPeople
import dev.kyralabs.pingogram.feature.chat.ui.PinnedEvent
import dev.kyralabs.pingogram.feature.chat.ui.ThreadRepositories
import dev.kyralabs.pingogram.feature.chat.ui.senderNameOf
import dev.kyralabs.pingogram.feature.chat.ui.shortNameOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The pinned messages of chat [chatId] (of its forum topic or message thread [topic], if not null) for `ChatViewModel`
 * (`REQUIREMENTS.md` FR-C9): the bar over the conversation
 * ([bar]) and the sheet that lists them all ([sheet], kept out of the screen's state like the forward sheet).
 *
 * - **Pins** come from `PinnedMessageRepository.pinnedMessages`, newest first, and follow every pin and unpin.
 * - **Cycling** ([PinnedEvent.ShowCurrent]): a tap shows the bar's message in the list ([onShow], the jump a reply
 *   quote makes) and moves the bar on to the next older one, back to the newest after the oldest, as Telegram's apps
 *   do. A message tapped in the sheet becomes the bar's. The bar starts at the newest, and goes back there when its
 *   message is unpinned.
 * - **Closing** ([PinnedEvent.Close]): where the user may unpin the message (TDLib's `can_be_pinned` of the newest pin,
 *   [MessageActionRepository.properties]) it is unpinned ([MessageActionRepository.unpin]); for others the bar hides on
 *   this device ([HiddenPinsRepository]) until a newer message is pinned, with a snackbar that says so.
 * - **Names** follow [chat] and [people]: "You" for the user's own messages, none where the chat itself posts.
 *
 * Runs in [scope], the view model's, on the main thread; [onEffect] gets the snackbars.
 */
@Suppress("LongParameterList") // The chat, where its pins come from and go, and what names their senders.
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatPinnedMessages(
    private val chatId: Long,
    private val topic: MessageTopic?,
    repositories: ThreadRepositories,
    private val actions: MessageActionRepository,
    chat: Flow<Chat>,
    people: Flow<ChatPeople>,
    private val scope: CoroutineScope,
    private val onShow: (messageId: Long) -> Unit,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val hidden: HiddenPinsRepository = repositories.hiddenPins

    private val pins: Flow<ImmutableList<Message>> =
        repositories.pinned
            .pinnedMessages(chatId, topic)
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    /** The bar's message, by id; null for the newest. */
    private val shown = MutableStateFlow<Long?>(null)

    private val listOpen = MutableStateFlow(false)

    /** Whether the user may unpin the chat's pins, asked of TDLib for its newest one. */
    private val canUnpin: Flow<Boolean> =
        pins
            .map { it.firstOrNull()?.id }
            .distinctUntilChanged()
            .mapLatest { id -> id != null && actions.properties(chatId, id).getOrNull()?.canBePinned == true }

    private val named: Flow<Named> = combine(pins, chat, people, ::Named)

    /** The bar; null while the chat and its pins load, while it has none, and while the user hid them. */
    val bar: Flow<PinnedBar?> =
        combine(named, shown, hidden.hiddenUpTo(chatId, topic), canUnpin) { named, shownId, hiddenUpTo, canUnpin ->
            val pins = named.pins
            val newest = pins.firstOrNull()
            val index = pins.indexOfFirst { it.id == shownId }.coerceAtLeast(0)
            if (newest == null || (hiddenUpTo != null && newest.id <= hiddenUpTo)) {
                null
            } else {
                val message = pins[index]
                PinnedBar(message.id, index, pins.size, named.senderOf(message), named.previewOf(message), canUnpin)
            }
        }.onStart { emit(null) }
            .distinctUntilChanged()

    /** The sheet of every pinned message; null while it is closed, or there are none. */
    val sheet: StateFlow<PinnedSheet?> =
        combine(listOpen, named, shown) { open, named, shownId ->
            if (!open || named.pins.isEmpty()) {
                null
            } else {
                val rows = named.pins.map { PinnedRow(it.id, named.senderOf(it), named.previewOf(it)) }
                PinnedSheet(rows.toImmutableList(), shownId ?: named.pins.first().id)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun onEvent(event: PinnedEvent) {
        when (event) {
            PinnedEvent.ShowCurrent -> showCurrent()
            PinnedEvent.Close -> close()
            PinnedEvent.OpenList -> listOpen.value = true
            PinnedEvent.DismissList -> listOpen.value = false
            is PinnedEvent.ShowFromList -> showFromList(event.messageId)
        }
    }

    private fun showCurrent() {
        scope.launch {
            val pins = pins.first()
            if (pins.isEmpty()) return@launch
            val index = pins.indexOfFirst { it.id == shown.value }.coerceAtLeast(0)
            onShow(pins[index].id)
            shown.value = pins[(index + 1) % pins.size].id
        }
    }

    private fun showFromList(messageId: Long) {
        listOpen.value = false
        shown.value = messageId
        onShow(messageId)
    }

    private fun close() {
        scope.launch {
            val pins = pins.first()
            val newest = pins.firstOrNull() ?: return@launch
            val message = pins.firstOrNull { it.id == shown.value } ?: newest
            if (canUnpin.first()) {
                val unpinned = actions.unpin(chatId, message.id)
                if (unpinned is Outcome.Failure) onEffect(ChatEffect.ShowError(unpinned.error))
            } else {
                when (val hid = hidden.hide(chatId, newest.id, topic)) {
                    is Outcome.Success -> onEffect(ChatEffect.PinnedHidden)
                    is Outcome.Failure -> onEffect(ChatEffect.ShowError(hid.error))
                }
            }
        }
    }

    /** The pins with what names their senders. */
    private class Named(
        val pins: ImmutableList<Message>,
        val chat: Chat,
        val people: ChatPeople,
    ) {
        /** "You", or the sender's name, isolated for the string it goes into; null where the chat itself posts. */
        fun senderOf(message: Message) =
            if ((message.sender as? MessageSender.Chat)?.chatId == chat.id && !message.isOutgoing) {
                null
            } else {
                senderNameOf(message, chat, people).bidiIsolated()
            }

        fun previewOf(message: Message) =
            messagePreviewText(message, people.myUserId) { sender -> shortNameOf(sender, chat, people) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
