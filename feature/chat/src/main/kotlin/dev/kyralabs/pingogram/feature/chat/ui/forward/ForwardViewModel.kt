package dev.kyralabs.pingogram.feature.chat.ui.forward

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.repository.ChatLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageActionRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.SearchRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.ForwardRoute
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ForwardEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The forward destination ([ForwardRoute], `docs/ARCHITECTURE.md` Navigation): the forward sheet of the route's
 * messages for screens of other features, the media viewer's Forward. The conversation shows the same sheet in place
 * (`ChatViewModel`); both run a [ChatForwarding]. The route comes from the [SavedStateHandle], so the sheet opens again
 * after process death.
 *
 * - **Opening**: the messages, from memory or TDLib ([MessageRepository.message], all at once), go to
 *   [ChatForwarding.open], which keeps those TDLib lets be forwarded. Starts after the frame that creates it.
 * - **Sending** forwards in [applicationScope]: the destination leaves as the sheet closes, and its view model with
 *   it, but the forwarding goes on.
 * - **Results** ([ChatEffect.Forwarded], [ChatEffect.ForwardFailed], and why the sheet could not open:
 *   [ChatEffect.ShowError], [ChatEffect.ActionUnavailable]) go to [results], whose snackbars the screen below shows
 *   (`ForwardNotices`).
 * - [closed] turns true once the sheet closed, sent or dismissed, or could not open: the destination leaves then.
 *
 * It takes the repositories the sheet combines, plus the dispatcher, scope and results that tests replace, hence
 * `LongParameterList` is suppressed.
 */
@Suppress("LongParameterList")
@HiltViewModel
class ForwardViewModel internal constructor(
    route: ForwardRoute,
    chats: ChatRepository,
    messages: MessageRepository,
    search: SearchRepository,
    users: UserRepository,
    groups: GroupRepository,
    actions: MessageActionRepository,
    dispatcher: CoroutineDispatcher,
    applicationScope: CoroutineScope,
    private val results: ForwardResults,
    hiddenChats: Flow<Set<Long>> = flowOf(emptySet()),
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        chats: ChatRepository,
        messages: MessageRepository,
        search: SearchRepository,
        users: UserRepository,
        groups: GroupRepository,
        actions: MessageActionRepository,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
        @ApplicationScope applicationScope: CoroutineScope,
        results: ForwardResults,
        chatLock: ChatLockRepository,
    ) : this(
        savedStateHandle.toRoute<ForwardRoute>(),
        chats,
        messages,
        search,
        users,
        groups,
        actions,
        dispatcher,
        applicationScope,
        results,
        chatLock.hiddenChatIds.filterNotNull(),
    )

    private val closedState = MutableStateFlow(false)

    /** The sheet closed, or could not open: the destination leaves. */
    val closed: StateFlow<Boolean> = closedState.asStateFlow()

    private val forwarding =
        ChatForwarding(
            fromChatId = route.fromChatId,
            chats = chats,
            search = search,
            users = users,
            groups = groups,
            actions = actions,
            scope = viewModelScope,
            dispatcher = dispatcher,
            sendScope = applicationScope,
            onClosed = { closedState.value = true },
            hiddenChats = hiddenChats,
            onEffect = ::onEffect,
        )

    /** The forward sheet; null until it opens, and once it closed. */
    val sheet: StateFlow<ForwardSheet?> get() = forwarding.sheet

    /** The comment of the sheet, for its text field. */
    val comment: ComposerText get() = forwarding.comment

    init {
        // Dispatched: after the frame whose composition created the view model (`docs/PERFORMANCE.md` §4).
        viewModelScope.launch(Dispatchers.Main) {
            val answers = route.messageIds.distinct().map { id -> async { messages.message(route.fromChatId, id) } }
            val found = answers.awaitAll()
            val failure = found.firstNotNullOfOrNull { it as? Outcome.Failure }
            if (failure != null) {
                onEffect(ChatEffect.ShowError(failure.error))
            } else {
                forwarding.open(found.map { (it as Outcome.Success<Message>).value })
            }
        }
    }

    /** Handles what the user does in the sheet. */
    fun onEvent(event: ForwardEvent) {
        forwarding.onEvent(event)
    }

    /** A result for the screen below; the sheet closes if it could not open. */
    private fun onEffect(effect: ChatEffect) {
        results.send(effect)
        if (effect is ChatEffect.ShowError || effect == ChatEffect.ActionUnavailable) closedState.value = true
    }
}
