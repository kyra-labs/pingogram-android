package dev.kyralabs.pingogram.feature.chat.ui.sendas

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableSender
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.SendAsRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.SendAsEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Send-as in chat [chatId] for `ChatViewModel` (`REQUIREMENTS.md` FR-C5): who new messages go out as, for the
 * composer's avatar ([button]), and the picker ([sheet]) that changes it.
 *
 * - **Button**: the chat's `Chat.messageSender`, which TDLib sets only where the user can choose (groups and channels
 *   where they may post as a channel they own or anonymously), with its name and photo, following every change
 *   (`updateChatMessageSender`); null elsewhere, which hides the button.
 * - **Opening** ([SendAsEvent.Open]): the picker opens at once and asks Telegram who the user can send as
 *   ([SendAsRepository.availableSenders]), in TDLib's order, the current one marked; a failure closes it with a
 *   snackbar ([ChatEffect.ShowError]).
 * - **Choosing** ([SendAsEvent.Choose]): the tapped sender shows chosen at once and other taps wait while TDLib takes
 *   it; the picker closes once it did, and a failure marks the sender before it again, with a snackbar. Senders that need Telegram Premium cannot be chosen; the
 *   current one just closes the picker.
 *
 * Runs in [scope], the view model's, on the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatSendAs(
    private val chatId: Long,
    private val chats: ChatRepository,
    private val users: UserRepository,
    private val repository: SendAsRepository,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val openSheet = MutableStateFlow<SendAsSheet?>(null)

    /** The picker while it is open; null while it is not. */
    val sheet: StateFlow<SendAsSheet?> = openSheet.asStateFlow()

    /** Asking Telegram for the senders of the open picker, which another [open] or dismissing cancels. */
    private var opening: Job? = null

    /** Who new messages go out as, then every change; null where the user cannot choose. */
    val button: Flow<SendAsChoice?> =
        chats
            .chat(chatId)
            .map { it?.messageSender }
            .distinctUntilChanged()
            .flatMapLatest { sender ->
                when (sender) {
                    null -> flowOf(null)
                    is MessageSender.User -> users.user(sender.userId).map { choiceOf(sender, it) }
                    is MessageSender.Chat -> chats.chat(sender.chatId).map { choiceOf(sender, it) }
                }
            }.distinctUntilChanged()

    fun onEvent(event: SendAsEvent) {
        when (event) {
            SendAsEvent.Open -> open()
            is SendAsEvent.Choose -> choose(event.sender)
            SendAsEvent.Dismiss -> dismiss()
        }
    }

    private fun open() {
        opening?.cancel()
        openSheet.value = SendAsSheet()
        opening =
            scope.launch {
                val current = chats.chat(chatId).first()?.messageSender
                when (val found = repository.availableSenders(chatId)) {
                    is Outcome.Success -> {
                        val options = found.value.map { optionOf(it, current) }.toImmutableList()
                        openSheet.update { it?.copy(options = options) }
                    }

                    is Outcome.Failure -> {
                        openSheet.value = null
                        onEffect(ChatEffect.ShowError(found.error))
                    }
                }
            }
    }

    private fun choose(sender: MessageSender) {
        val sheet = openSheet.value ?: return
        val option = sheet.options?.firstOrNull { it.sender == sender }
        when {
            sheet.isChanging || option == null || option.needsPremium -> {
                return
            }

            option.selected -> {
                dismiss()
            }

            else -> {
                // The tapped sender shows chosen at once; the one before comes back if TDLib refuses it.
                openSheet.value = sheet.choosing(sender).copy(isChanging = true)
                scope.launch {
                    when (val changed = repository.setSender(chatId, sender)) {
                        is Outcome.Success -> {
                            openSheet.value = null
                        }

                        is Outcome.Failure -> {
                            // Unless dismissed meanwhile: as it was before the tap.
                            openSheet.update { open -> if (open == null) null else sheet }
                            onEffect(ChatEffect.ShowError(changed.error))
                        }
                    }
                }
            }
        }
    }

    /** Closes the picker; a new sender already on its way to TDLib still goes. */
    private fun dismiss() {
        opening?.cancel()
        openSheet.value = null
    }

    /** [available] in the picker, with its name and photo, looked up in memory or asked from TDLib. */
    private suspend fun optionOf(
        available: AvailableSender,
        current: MessageSender?,
    ): SendAsOption {
        val sender = available.sender
        val choice: SendAsChoice
        val kind: SenderKind
        when (sender) {
            is MessageSender.User -> {
                choice = choiceOf(sender, (users.getUser(sender.userId) as? Outcome.Success)?.value)
                kind = SenderKind.Account
            }

            is MessageSender.Chat -> {
                val chat = (chats.getChat(sender.chatId) as? Outcome.Success)?.value
                choice = choiceOf(sender, chat)
                kind =
                    when {
                        sender.chatId == chatId -> SenderKind.Anonymous
                        (chat?.type as? ChatType.Supergroup)?.isChannel == true -> SenderKind.Channel
                        else -> SenderKind.Group
                    }
            }
        }
        return SendAsOption(
            sender = sender,
            id = choice.id,
            name = choice.name,
            photo = choice.photo,
            kind = kind,
            selected = sender == current,
            needsPremium = available.needsPremium,
        )
    }
}

/** The sheet with [sender] chosen in place of the sender chosen before. */
private fun SendAsSheet.choosing(sender: MessageSender): SendAsSheet =
    copy(options = options?.map { it.copy(selected = it.sender == sender) }?.toImmutableList())

/** User [sender]'s avatar: their [user]'s name and photo, an unnamed placeholder while TDLib has not announced them. */
private fun choiceOf(
    sender: MessageSender.User,
    user: User?,
) = SendAsChoice(sender.userId, user?.fullName() ?: UNKNOWN_NAME, user?.profilePhoto)

/** Chat [sender]'s avatar: its [chat]'s title and photo, an unnamed placeholder while TDLib has not announced it. */
private fun choiceOf(
    sender: MessageSender.Chat,
    chat: Chat?,
) = SendAsChoice(sender.chatId, chat?.title?.let(UiText::Plain) ?: UNKNOWN_NAME, chat?.photo)

/** The name of a sender TDLib does not know yet: none, so its avatar shows the person placeholder. */
private val UNKNOWN_NAME = UiText.Plain("")
