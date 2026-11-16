package dev.kyralabs.pingogram.feature.chat.ui.actionbar

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatActionBar
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.BlockListRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatActionBarRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.chat.ui.ActionBarEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The action bar of chat [chatId] (td_api.tl `chat.action_bar`, `docs/DESIGN_SYSTEM.md` §6 "Chat action bar"): what
 * TDLib suggests doing about a chat someone new started, with that person's name. Blocking asks first ([blockDialog],
 * kept across process death), then blocks the other user and dismisses the bar; "Add contact" adds them by their
 * name; "Share my phone number" shares it with them; the close button dismisses the bar (`removeChatActionBar`). Each
 * only on the user's tap; results and refusals reach the conversation's snackbar through [effects]. TDLib then reports
 * the bar's change, which [bar] follows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ChatActionBarViewModel.Factory::class)
@Suppress("LongParameterList") // The chat, its saved state, and one repository per action the bar offers.
class ChatActionBarViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        private val savedStateHandle: SavedStateHandle,
        chats: ChatRepository,
        private val users: UserRepository,
        private val contacts: ContactRepository,
        private val blockList: BlockListRepository,
        private val actionBars: ChatActionBarRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): ChatActionBarViewModel
        }

        /** The other user of a private chat; null for other chats. */
        private val peer: StateFlow<Long?> =
            chats
                .chat(chatId)
                .map { (it?.type as? ChatType.Private)?.userId }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** The bar; null while there is none. */
        val bar: StateFlow<ActionBarUi?> =
            chats
                .chat(chatId)
                .map { chat -> chat?.actionBar?.let { it to (chat.type as? ChatType.Private)?.userId } }
                .distinctUntilChanged()
                .flatMapLatest { shown ->
                    when {
                        shown == null -> {
                            flowOf(null)
                        }

                        shown.second == null -> {
                            flowOf(ActionBarUi(shown.first, name = null))
                        }

                        else -> {
                            users
                                .user(
                                    shown.second!!,
                                ).map { ActionBarUi(shown.first, it?.firstName?.ifBlank { null }) }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        /** Whether the question before blocking shows. */
        val blockDialog: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_BLOCK, false)

        private val effectChannel = Channel<ActionBarEffect>(Channel.BUFFERED)

        /** What the conversation's snackbar says. */
        val effects: Flow<ActionBarEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: ActionBarEvent) {
            when (event) {
                ActionBarEvent.Block -> savedStateHandle[KEY_BLOCK] = true
                ActionBarEvent.DismissBlock -> savedStateHandle[KEY_BLOCK] = false
                ActionBarEvent.ConfirmBlock -> block()
                ActionBarEvent.AddContact -> addContact()
                ActionBarEvent.SharePhoneNumber -> sharePhoneNumber()
                ActionBarEvent.Dismiss -> viewModelScope.launch { report(actionBars.dismiss(chatId), null) }
            }
        }

        private fun block() {
            savedStateHandle[KEY_BLOCK] = false
            act(ActionBarEffect.Blocked) { userId ->
                blockList.block(MessageSender.User(userId)).also { blocked ->
                    // Blocked: the bar has nothing more to offer.
                    if (blocked is Outcome.Success) actionBars.dismiss(chatId)
                }
            }
        }

        private fun addContact() =
            act(ActionBarEffect.ContactAdded) { userId ->
                val user: User? = users.user(userId).first()
                contacts.save(userId, user?.firstName.orEmpty(), user?.lastName.orEmpty(), sharePhoneNumber = false)
            }

        private fun sharePhoneNumber() = act(ActionBarEffect.PhoneNumberShared) { actionBars.sharePhoneNumber(it) }

        /** Runs [request] for the other user, then says [done] or why it failed. */
        private fun act(
            done: ActionBarEffect,
            request: suspend (userId: Long) -> Outcome<Unit>,
        ) {
            val userId = peer.value ?: return
            viewModelScope.launch { report(request(userId), done) }
        }

        private fun report(
            result: Outcome<Unit>,
            done: ActionBarEffect?,
        ) {
            when (result) {
                is Outcome.Success -> done?.let(effectChannel::trySend)
                is Outcome.Failure -> effectChannel.trySend(ActionBarEffect.Failed(result.error))
            }
        }

        private companion object {
            const val KEY_BLOCK = "actionBar.block"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** The bar, and the other user's first [name] where the chat is a private one (null while unknown). */
@Immutable
data class ActionBarUi(
    val bar: ChatActionBar,
    val name: String?,
)

/** What the conversation's snackbar says after an action of the bar. */
sealed interface ActionBarEffect {
    data object Blocked : ActionBarEffect

    data object ContactAdded : ActionBarEffect

    data object PhoneNumberShared : ActionBarEffect

    data class Failed(
        val error: DomainError,
    ) : ActionBarEffect
}
