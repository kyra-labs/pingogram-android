package dev.kyralabs.pingogram.feature.chat.ui.poll

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.domain.model.PollVoter
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.PollRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.text.isDeleted
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.PollEffect
import dev.kyralabs.pingogram.feature.chat.ui.PollEvent
import dev.kyralabs.pingogram.feature.chat.ui.findById
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The polls of chat [chatId]'s messages (`REQUIREMENTS.md` FR-C4), for `ChatViewModel`: voting, taking a vote back,
 * stopping a poll, and the voters' sheet. Every request is one tap of the user's (Telegram's API terms). Each looks
 * the poll up in its message as it is now ([messageOf]), which `updatePoll` keeps current, so a poll that closed or
 * was voted in elsewhere counts at once; a vote that is no longer possible says so ([ChatEffect.ActionUnavailable]).
 *
 * - **Voting**: a tap on an answer votes for it ([PollEvent.Vote]); in a poll with several answers, taps check and
 *   uncheck them ([choices], copied into their bubble only, kept across process death) and "Vote" sends them
 *   ([PollEvent.SubmitVotes]). TDLib shows the vote on its way in the poll itself (`is_being_chosen`); a second vote
 *   meanwhile does nothing. Once Telegram took a quiz's answer, whether it was right goes out as
 *   [PollEffect.QuizAnswered], for its haptic.
 * - **Taking a vote back** ([retract]) and **stopping** ([askStop], after the [stopDialog]) come from the message
 *   actions, which check what TDLib allows first.
 * - **Voters** ([votersSheet]): for a public poll whose voters TDLib lists, a section per answer someone chose, each
 *   loading its first page as the sheet opens and the next on "Show more" ([PollEvent.LoadMoreVoters]), the users
 *   named as [UserRepository] knows them and chats by their title. A tapped voter closes the sheet and opens their
 *   profile ([ChatEffect.OpenProfile]) or chat ([ChatEffect.OpenChat]). Closing the sheet cancels its loads.
 * - Failures are [ChatEffect.ShowError] snackbars, except a page of voters, whose section says it failed.
 *
 * Runs in [scope], the view model's, on the main thread.
 */
@Suppress("LongParameterList", "TooManyFunctions") // What polls reach, and one function per event.
internal class ChatPolls(
    private val chatId: Long,
    private val repository: PollRepository,
    private val users: UserRepository,
    private val chats: ChatRepository,
    private val history: Flow<ChatHistory>,
    private val messageOf: (messageId: Long) -> Message?,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val mutableChoices =
        MutableStateFlow(savedState.get<ArrayList<String>>(KEY_CHOICES)?.let(::choicesOf) ?: persistentMapOf())

    /** The answers checked in polls with several answers, not sent yet, by message id. */
    val choices: StateFlow<PersistentMap<Long, PersistentSet<Int>>> = mutableChoices.asStateFlow()

    private val openDialog = MutableStateFlow<StopPollDialog?>(null)

    /** The question before a poll is stopped; null while none shows. */
    val stopDialog: StateFlow<StopPollDialog?> = openDialog.asStateFlow()

    private val voters = MutableStateFlow<PollVotersSheet?>(null)

    /** The voters' sheet, with the users and chats it names; null while it is closed. */
    val votersSheet: StateFlow<PollVotersSheet?> =
        combine(voters, users.users) { sheet, known -> sheet?.named(known) }
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The loads of the open voters' sheet, cancelled with it. */
    private var votersJob: Job? = null

    /** The messages whose vote is on its way. */
    private val voting = mutableSetOf<Long>()

    fun onEvent(event: PollEvent) {
        when (event) {
            is PollEvent.Vote -> vote(event.messageId, event.optionIndex)

            is PollEvent.Toggle -> toggle(event.messageId, event.optionIndex)

            is PollEvent.SubmitVotes -> submit(event.messageId)

            PollEvent.ConfirmStop -> confirmStop()

            PollEvent.DismissStop -> openDialog.value = null

            is PollEvent.OpenVoters -> openVoters(event.messageId)

            is PollEvent.LoadMoreVoters -> loadVoters(event.optionIndex)

            is PollEvent.OpenVoter -> openVoter(event.sender)

            PollEvent.DismissVoters -> dismissVoters()

            // Through the message actions, which check what TDLib allows: retract and askStop.
            is PollEvent.RetractVote, is PollEvent.StopPoll -> Unit
        }
    }

    /** Takes the user's vote back in the poll of message [messageId], where it still can be. */
    fun retract(messageId: Long) {
        val poll = pollOf(messageId)
        if (poll?.canRetractVote == true) send(messageId, emptyList(), poll) else onEffect(ChatEffect.ActionUnavailable)
    }

    /** Asks before stopping the poll of message [messageId], if it is still open. */
    fun askStop(messageId: Long) {
        if (pollOf(messageId)?.isClosed == false) {
            openDialog.value = StopPollDialog(messageId)
        } else {
            onEffect(ChatEffect.ActionUnavailable)
        }
    }

    private fun vote(
        messageId: Long,
        optionIndex: Int,
    ) {
        val poll = votablePoll(messageId, optionIndex)
        if (poll == null || poll.allowsMultipleAnswers) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        send(messageId, listOf(optionIndex), poll)
    }

    private fun toggle(
        messageId: Long,
        optionIndex: Int,
    ) {
        if (votablePoll(messageId, optionIndex)?.allowsMultipleAnswers != true) return
        setChoices { all ->
            val checked = all[messageId].orEmpty()
            val toggled = if (optionIndex in checked) checked - optionIndex else checked + optionIndex
            if (toggled.isEmpty()) all.remove(messageId) else all.put(messageId, toggled.toPersistentSet())
        }
    }

    private fun submit(messageId: Long) {
        val poll = pollOf(messageId)
        val checked = mutableChoices.value[messageId].orEmpty()
        if (poll == null || !poll.canVote || checked.isEmpty()) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        send(messageId, checked.sorted(), poll)
    }

    /** Sends [optionIndexes] for [poll] of message [messageId] (none takes the vote back), unless one is on its way. */
    private fun send(
        messageId: Long,
        optionIndexes: List<Int>,
        poll: Poll,
    ) {
        if (!voting.add(messageId)) return
        scope.launch {
            try {
                when (val result = repository.vote(chatId, messageId, optionIndexes)) {
                    is Outcome.Failure -> {
                        onEffect(ChatEffect.ShowError(result.error))
                    }

                    is Outcome.Success -> {
                        setChoices { it.remove(messageId) }
                        if (poll.type is PollType.Quiz && optionIndexes.isNotEmpty()) reportQuizAnswer(messageId)
                    }
                }
            } finally {
                voting.remove(messageId)
            }
        }
    }

    /**
     * Whether the user's answer to the quiz of message [messageId] was right, once the poll shows it (`updatePoll` may
     * come after TDLib's answer): every chosen answer correct. Nothing if it does not show in time.
     */
    private suspend fun reportQuizAnswer(messageId: Long) {
        val answered =
            withTimeoutOrNull(QUIZ_ANSWER_TIMEOUT) {
                history.mapNotNull { it.messages.findById(messageId)?.poll }.first { it.hasVoted }
            } ?: return
        onEffect(PollEffect.QuizAnswered(correct = answered.options.filter { it.isChosen }.all { it.isCorrect }))
    }

    private fun confirmStop() {
        val dialog = openDialog.value ?: return
        openDialog.value = null
        scope.launch {
            val result = repository.stop(chatId, dialog.messageId)
            if (result is Outcome.Failure) onEffect(ChatEffect.ShowError(result.error))
        }
    }

    /** Opens the voters' sheet of the poll of message [messageId], where TDLib lists them, and loads each answer's. */
    private fun openVoters(messageId: Long) {
        val poll = pollOf(messageId)
        if (poll == null || poll.isAnonymous || !poll.canGetVoters) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        dismissVoters()
        val sections =
            poll.options
                .filter { it.voterCount > 0 }
                .map { PollVoterSection(it.index, it.text.text, it.voterCount) }
        voters.value = PollVotersSheet(messageId, poll.question.text, sections.toImmutableList())
        votersJob = Job(scope.coroutineContext[Job])
        sections.forEach { loadVoters(it.optionIndex) }
    }

    /** Loads the next page of answer [optionIndex]'s voters, unless one is on its way or there are no more. */
    private fun loadVoters(optionIndex: Int) {
        val sheet = voters.value
        val job = votersJob
        val section =
            sheet?.sections?.firstOrNull { it.optionIndex == optionIndex }?.takeUnless { it.isLoading || it.reachedEnd }
        if (sheet == null || job == null || section == null) return
        updateSection(optionIndex) { it.copy(isLoading = true, error = null) }
        scope.launch(job) {
            val page = repository.voters(chatId, sheet.messageId, optionIndex, section.voters.size, PAGE_SIZE)
            when (page) {
                is Outcome.Failure -> {
                    updateSection(optionIndex) { it.copy(isLoading = false, error = page.error) }
                }

                is Outcome.Success -> {
                    val rows = page.value.voters.map { rowOf(it) }
                    updateSection(optionIndex) { current ->
                        val known = current.voters.mapTo(mutableSetOf()) { it.sender }
                        val added = rows.filter { it.sender !in known }
                        current.copy(
                            voters = (current.voters + added).toImmutableList(),
                            count = maxOf(page.value.totalCount, current.voters.size + added.size),
                            isLoading = false,
                            reachedEnd = added.isEmpty() || current.voters.size + added.size >= page.value.totalCount,
                        )
                    }
                }
            }
        }
    }

    /** A voter's row; users are named from [UserRepository] as the sheet shows ([named]), chats by their title. */
    private suspend fun rowOf(voter: PollVoter): PollVoterRow =
        when (val sender = voter.sender) {
            is MessageSender.User -> {
                if (users.users.first()[sender.userId] == null) users.getUser(sender.userId)
                PollVoterRow(sender, sender.userId, UiText.Plain(""), photo = null, date = voter.date)
            }

            is MessageSender.Chat -> {
                val chat = chats.chat(sender.chatId).first() ?: chats.getChat(sender.chatId).getOrNull()
                PollVoterRow(sender, sender.chatId, UiText.Plain(chat?.title.orEmpty()), chat?.photo, voter.date)
            }
        }

    private fun openVoter(sender: MessageSender) {
        dismissVoters()
        when (sender) {
            is MessageSender.User -> onEffect(ChatEffect.OpenProfile(sender.userId))
            is MessageSender.Chat -> onEffect(ChatEffect.OpenChat(sender.chatId))
        }
    }

    private fun dismissVoters() {
        votersJob?.cancel()
        votersJob = null
        voters.value = null
    }

    private fun updateSection(
        optionIndex: Int,
        change: (PollVoterSection) -> PollVoterSection,
    ) {
        voters.update { sheet ->
            sheet?.copy(
                sections =
                    sheet.sections
                        .map { if (it.optionIndex == optionIndex) change(it) else it }
                        .toImmutableList(),
            )
        }
    }

    private fun setChoices(
        change: (PersistentMap<Long, PersistentSet<Int>>) -> PersistentMap<Long, PersistentSet<Int>>,
    ) {
        val changed = change(mutableChoices.value)
        mutableChoices.value = changed
        savedState[KEY_CHOICES] =
            changed.entries.mapTo(ArrayList()) { (id, checked) -> "$id:${checked.joinToString(",")}" }
    }

    private fun pollOf(messageId: Long): Poll? = messageOf(messageId)?.poll

    /** The poll of message [messageId] if the user can vote in it now and it has answer [optionIndex]. */
    private fun votablePoll(
        messageId: Long,
        optionIndex: Int,
    ): Poll? = pollOf(messageId)?.takeIf { poll -> poll.canVote && poll.options.any { it.index == optionIndex } }

    private companion object {
        const val KEY_CHOICES = "poll_choices"
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Voters per page: TDLib gives at most 50, and the sheet shows a page per answer at once. */
        const val PAGE_SIZE = 20

        val QUIZ_ANSWER_TIMEOUT = 10.seconds
    }
}

/** The poll of this message; null for other content. */
internal val Message.poll: Poll? get() = (content as? MessageContent.Poll)?.poll

/** The users of [this] named as [known] has them, a deleted account with its placeholder; the others as they are. */
private fun PollVotersSheet.named(known: Map<Long, User>): PollVotersSheet =
    copy(
        sections =
            sections
                .map { section ->
                    section.copy(
                        voters =
                            section.voters
                                .map { row ->
                                    val user = (row.sender as? MessageSender.User)?.let { known[it.userId] }
                                    if (user == null) {
                                        row
                                    } else {
                                        row.copy(
                                            name = user.fullName(),
                                            photo = user.profilePhoto,
                                            placeholderIcon = if (user.isDeleted) R.drawable.ic_person_filled else null,
                                        )
                                    }
                                }.toImmutableList(),
                    )
                }.toImmutableList(),
    )

/** The checked answers kept in the saved state: "messageId:index,index" per poll. */
private fun choicesOf(saved: List<String>): PersistentMap<Long, PersistentSet<Int>> =
    saved
        .mapNotNull { entry ->
            val messageId = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            val indexes = entry.substringAfter(':').split(',').mapNotNull(String::toIntOrNull)
            if (indexes.isEmpty()) null else messageId to indexes.toPersistentSet()
        }.toMap()
        .toPersistentMap()

/**
 * The state with the answers checked in polls ([ChatPolls.choices]) copied into their bubbles only, so no other
 * recomposes; the state as it is while none is checked.
 */
internal fun ChatUiState.withPollChoices(choices: PersistentMap<Long, PersistentSet<Int>>): ChatUiState {
    val content = content as? ChatContent.Content
    if (choices.isEmpty() || content == null) return this
    var changed = false
    val items =
        content.items.map { item ->
            val bubble = item as? ChatItem.Bubble
            val checked = bubble?.let { choices[it.id] }
            if (bubble == null || checked == null) {
                item
            } else {
                changed = true
                bubble.copy(pollChoices = checked)
            }
        }
    return if (changed) copy(content = content.copy(items = items.toImmutableList())) else this
}
