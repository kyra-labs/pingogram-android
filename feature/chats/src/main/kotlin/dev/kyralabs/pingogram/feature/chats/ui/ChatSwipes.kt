package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What swiping a chat row does (Appearance → Chat list → Swipe actions): toward the end ([start], from the start
 * edge) and toward the start ([end]). The defaults are read and archive, until the settings are read. With
 * [changesFolder], rows do not swipe: swiping the list shows the folder tab beside the selected one ([folderSwipe]).
 */
@Immutable
data class ChatSwipes(
    val start: SwipeAction = SwipeAction.Read,
    val end: SwipeAction = SwipeAction.Archive,
    val changesFolder: Boolean = false,
)

/**
 * The swipes the rows of a chat list take, provided by its route from [ChatSwipesViewModel]. Static: it changes only
 * when the user picks other swipes, and every row reads it.
 */
internal val LocalChatSwipes = staticCompositionLocalOf { ChatSwipes() }

/** The chat list's swipes, read from the device's settings ([ChatListSettingsRepository]) for its route to pass on. */
@HiltViewModel
class ChatSwipesViewModel
    @Inject
    constructor(
        repository: ChatListSettingsRepository,
    ) : ViewModel() {
        val swipes: StateFlow<ChatSwipes> =
            repository.settings
                .map { ChatSwipes(it.startSwipe, it.endSwipe, it.swipeChangesFolder) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatSwipes())

        private companion object {
            /** Keeps the swipes through a rotation, as every screen's view model does. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * The action [swipe] does on this row, as the row allows it: read or unread, archive or unarchive, pin or unpin, mute
 * or unmute (none where the chat cannot be muted), delete (which asks first); null for [SwipeAction.None], a direction
 * the row does not swipe.
 */
internal fun ChatListItemModel.rowActionFor(swipe: SwipeAction): ChatRowAction? =
    when (swipe) {
        SwipeAction.Read -> either(ChatRowAction.MarkAsRead, ChatRowAction.MarkAsUnread)
        SwipeAction.Archive -> either(ChatRowAction.Unarchive, ChatRowAction.Archive)
        SwipeAction.Pin -> either(ChatRowAction.Unpin, ChatRowAction.Pin)
        SwipeAction.Mute -> either(ChatRowAction.Unmute, ChatRowAction.Mute)
        SwipeAction.Delete -> ChatRowAction.Delete.takeIf { it in actions }
        SwipeAction.None -> null
    }

/** [first] where the row has it, else [second] where it has that, else nothing. */
private fun ChatListItemModel.either(
    first: ChatRowAction,
    second: ChatRowAction,
): ChatRowAction? =
    when {
        first in actions -> first
        second in actions -> second
        else -> null
    }
