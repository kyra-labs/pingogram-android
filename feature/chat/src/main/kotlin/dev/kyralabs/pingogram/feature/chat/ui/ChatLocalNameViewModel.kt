package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.domain.repository.shownName
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * The name chat [chatId] was given on this phone (`REQUIREMENTS.md` FR-X20), which the conversation's top bar shows in
 * place of its title ([withLocalName]); null for none, while the setting is off, and for a contact, whose contact
 * name shows. A view model of its own, beside the conversation's, as the bookmarks and reminders are.
 */
@HiltViewModel(assistedFactory = ChatLocalNameViewModel.Factory::class)
class ChatLocalNameViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        localChatNames: LocalChatNamesRepository,
        userRepository: UserRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): ChatLocalNameViewModel
        }

        /** The name, then every change. A private chat's id is its user's, whose contact name wins. */
        val name: StateFlow<String?> =
            combine(localChatNames.names, userRepository.users) { names, users ->
                names.shownName(chatId, isContact = users[chatId]?.isContact == true)
            }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * This state with its top bar titled [name], the name given on this phone; as it is without one, and in a topic or a
 * thread ([inTopic]), whose header names the topic.
 */
internal fun ChatUiState.withLocalName(
    name: String?,
    inTopic: Boolean = false,
): ChatUiState {
    val shown = header?.takeUnless { inTopic } ?: return this
    return if (name == null) this else copy(header = shown.copy(title = UiText.Plain(name)))
}
