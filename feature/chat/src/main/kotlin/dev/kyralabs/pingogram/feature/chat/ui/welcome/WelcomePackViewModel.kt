package dev.kyralabs.pingogram.feature.chat.ui.welcome

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.WelcomeMessage
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.WelcomeMessageRepository
import dev.kyralabs.pingogram.core.domain.repository.WelcomePackRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The welcome pack of group [chatId] (the owner's board 6): the welcome messages its administrators set, which only
 * new members see (td_api.tl `chat.has_welcome_messages`, then `welcomeMessage` through [WelcomeMessageRepository]),
 * at the top of the conversation until the user collapses it with "Got it" ([WelcomePackRepository], kept on the
 * device); collapsed, a bar the user can open again for a while ([WelcomePackEvent.Expand], kept in the saved state).
 * Nothing is asked of Telegram where the chat has no welcome messages; one that cannot be loaded shows nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = WelcomePackViewModel.Factory::class)
class WelcomePackViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        private val savedStateHandle: SavedStateHandle,
        chats: ChatRepository,
        messages: WelcomeMessageRepository,
        private val packs: WelcomePackRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): WelcomePackViewModel
        }

        private val expanded = savedStateHandle.getStateFlow(KEY_EXPANDED, false)

        /** The pack; null where the group has none, or before it loaded. */
        val pack: StateFlow<WelcomePackUi?> =
            chats
                .chat(chatId)
                .map { chat -> chat?.hasWelcomeMessages == true && chat.isGroup }
                .distinctUntilChanged()
                .flatMapLatest { has ->
                    if (!has) {
                        flowOf(null)
                    } else {
                        combine(
                            messages.messages(chatId),
                            packs.isCollapsed(chatId),
                            expanded,
                        ) { loaded, collapsed, open ->
                            val items = loaded.getOrNull()?.filter { it.isSupported }?.map(WelcomeMessage::toItem)
                            items?.takeIf { it.isNotEmpty() }?.let {
                                WelcomePackUi(
                                    it.toImmutableList(),
                                    collapsed && !open,
                                )
                            }
                        }
                    }
                }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        fun onEvent(event: WelcomePackEvent) {
            when (event) {
                WelcomePackEvent.Collapse -> collapse()
                WelcomePackEvent.Expand -> savedStateHandle[KEY_EXPANDED] = true
            }
        }

        /** "Got it": the pack becomes a bar, for good on this device; a failure to keep it only lasts this time. */
        private fun collapse() {
            savedStateHandle[KEY_EXPANDED] = false
            viewModelScope.launch { packs.collapse(chatId) }
        }

        private companion object {
            const val KEY_EXPANDED = "welcome.expanded"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private val dev.kyralabs.pingogram.core.domain.model.Chat.isGroup: Boolean
    get() = (type as? ChatType.Supergroup)?.isChannel == false || type is ChatType.BasicGroup

private fun WelcomeMessage.toItem() = WelcomeItem(id, text, photo)

/** The pack: its [messages], and whether it shows as a bar ([collapsed]) rather than in full. */
@Immutable
data class WelcomePackUi(
    val messages: ImmutableList<WelcomeItem>,
    val collapsed: Boolean,
)

/** A welcome message: its [text] (a photo's caption) and its [photo], if any. */
@Immutable
data class WelcomeItem(
    val id: Int,
    val text: FormattedText,
    val photo: Photo?,
)

/** What the user does with the welcome pack. */
sealed interface WelcomePackEvent {
    /** "Got it": collapse it into a bar. */
    data object Collapse : WelcomePackEvent

    /** The bar was tapped: show the pack in full again. */
    data object Expand : WelcomePackEvent
}
