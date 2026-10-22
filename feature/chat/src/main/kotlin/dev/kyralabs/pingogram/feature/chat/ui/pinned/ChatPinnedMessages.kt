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
 * - **Pins** come from `PinnedMessageRepository.pinnedMessages`, newest first, and follow every pin and unpin. The bar
 *   counts them from the oldest, as the list shows them: the newest is the last ([PinnedBar.position]).
 * - **Following the list** ([setVisible]): the bar shows the newest pinned message at or above the newest message on
 *   screen, so at the bottom of the chat the newest pin, and an older one as the list scrolls up past it; above every
 *   pin, the oldest. Before the list reports anything, the newest.
 * - **Cycling** ([PinnedEvent.ShowCurrent]): a tap shows the bar's message in the list ([onShow], the jump a reply
 *   quote makes) and moves the bar on to the next older one, back to the newest after the oldest, as Telegram's apps
 *   do; a message tapped in the sheet becomes the bar's. That choice holds while the list moves to it, until the user
 *   drags the list ([PinnedEvent.ListDragged]) or the message is unpinned; then the bar follows the list again.
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

    /** The newest message on screen, by id; null until the list reports one. */
    private val newestVisible = MutableStateFlow<Long?>(null)

    /** The message a tap or the sheet chose for the bar, by id, until the user drags the list; null to follow it. */
    private val chosen = MutableStateFlow<Long?>(null)

    /** The bar's message, by id: the chosen one while it is pinned, else the one closest above the screen. */
    private val shown: Flow<Long?> =
        combine(pins, newestVisible, chosen) { pins, newestVisible, chosen ->
            pins.firstOrNull { it.id == chosen }?.id ?: closestAbove(pins, newestVisible)?.id
        }.distinctUntilChanged()

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
                val position = pins.size - 1 - index
                PinnedBar(message.id, position, pins.size, named.senderOf(message), named.previewOf(message), canUnpin)
            }
        }.onStart { emit(null) }
            .distinctUntilChanged()

    /** The sheet of every pinned message; null while it is closed, or there are none. */
    val sheet: StateFlow<PinnedSheet?> =
        combine(listOpen, named, shown) { open, named, shownId ->
            if (!open || named.pins.isEmpty()) {
                null
            } else {
                val rows = named.pins.asReversed().map { PinnedRow(it.id, named.senderOf(it), named.previewOf(it)) }
                PinnedSheet(rows.toImmutableList(), shownId ?: named.pins.first().id)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The list shows [messageIds] now; an empty set (the list left the screen) keeps the bar as it is. */
    fun setVisible(messageIds: Set<Long>) {
        messageIds.maxOrNull()?.let { newestVisible.value = it }
    }

    fun onEvent(event: PinnedEvent) {
        when (event) {
            PinnedEvent.ShowCurrent -> showCurrent()
            PinnedEvent.ListDragged -> chosen.value = null
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
            val shownId = shown.first()
            val index = pins.indexOfFirst { it.id == shownId }.coerceAtLeast(0)
            onShow(pins[index].id)
            chosen.value = pins[(index + 1) % pins.size].id
        }
    }

    private fun showFromList(messageId: Long) {
        listOpen.value = false
        chosen.value = messageId
        onShow(messageId)
    }

    private fun close() {
        scope.launch {
            val pins = pins.first()
            val newest = pins.firstOrNull() ?: return@launch
            val shownId = shown.first()
            val message = pins.firstOrNull { it.id == shownId } ?: newest
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

        /**
         * Of [pins] (newest first), the newest at or above message [newestVisible]: the closest pinned message above
         * the screen, or on it; the oldest above every pin; the newest while nothing is known of the screen.
         */
        fun closestAbove(
            pins: List<Message>,
            newestVisible: Long?,
        ): Message? =
            if (newestVisible == null) {
                pins.firstOrNull()
            } else {
                pins.firstOrNull { it.id <= newestVisible } ?: pins.lastOrNull()
            }
    }
}
