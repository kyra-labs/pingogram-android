package dev.kyralabs.pingogram.feature.chat.ui.birthday

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.domain.repository.DeclinedBirthdaysRepository
import dev.kyralabs.pingogram.core.domain.repository.PrivacyRepository
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import dev.kyralabs.pingogram.core.ui.component.BirthdaySuggestionStatus
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the birthday suggestions of chat [chatId] (td_api.tl `messageSuggestBirthdate`, the owner's board 16b) need
 * beyond their messages: whether each is answered, and the Birthday privacy setting their text names.
 *
 * A suggestion counts as accepted while the user's birthday is the one it suggests ([ProfileRepository]); declined
 * ones are kept on the device ([DeclinedBirthdaysRepository]), as Telegram keeps no answer for them. Nothing is asked
 * of Telegram until a card collects [answers], so chats without a suggestion ask nothing. "Accept" sets the birthday
 * (`setBirthdate`) only on the user's tap; a failure reaches the conversation's snackbar through [errors].
 */
@HiltViewModel(assistedFactory = BirthdaySuggestionsViewModel.Factory::class)
class BirthdaySuggestionsViewModel
    @AssistedInject
    constructor(
        @Assisted private val chatId: Long,
        private val profiles: ProfileRepository,
        privacy: PrivacyRepository,
        private val declinedSuggestions: DeclinedBirthdaysRepository,
    ) : ViewModel(),
        BirthdaySuggestionsHost {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): BirthdaySuggestionsViewModel
        }

        private val accepted = MutableStateFlow<Birthdate?>(null)
        private val accepting = MutableStateFlow<Long?>(null)
        private val errorChannel = Channel<DomainError>(Channel.BUFFERED)

        /** Telegram's refusals of an answer, for the conversation's snackbar. */
        val errors: Flow<DomainError> = errorChannel.receiveAsFlow()

        /** The user's own birthday, then the one they accepted here. */
        private val ownBirthdate: Flow<Birthdate?> =
            flow {
                emit(profiles.editableProfile().getOrNull()?.birthdate)
                emitAll(accepted.filterNotNull())
            }

        /** Who may see the birthday; every change, from before it is first read so none is missed. */
        private val birthdayPrivacy: Flow<PrivacyChoice?> =
            channelFlow {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    privacy.changes.collect { if (it.setting == PrivacySetting.Birthdate) send(it.rules.choice) }
                }
                send(privacy.rules(PrivacySetting.Birthdate).getOrNull()?.choice)
            }

        override val answers: StateFlow<BirthdayAnswers> =
            combine(
                ownBirthdate,
                birthdayPrivacy,
                declinedSuggestions.declined(chatId),
                accepting,
            ) { own, choice, declined, pending ->
                BirthdayAnswers(own, choice, declined.toImmutableSet(), pending)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BirthdayAnswers())

        override fun accept(
            messageId: Long,
            birthdate: Birthdate,
        ) {
            if (accepting.value != null) return
            accepting.value = messageId
            viewModelScope.launch {
                when (val result = profiles.setBirthdate(birthdate)) {
                    is Outcome.Success -> accepted.value = birthdate
                    is Outcome.Failure -> errorChannel.send(result.error)
                }
                accepting.value = null
            }
        }

        override fun decline(messageId: Long) {
            viewModelScope.launch {
                val result = declinedSuggestions.decline(chatId, messageId)
                if (result is Outcome.Failure) errorChannel.send(result.error)
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** What a conversation's birthday cards read and do; the conversation's route provides it. */
interface BirthdaySuggestionsHost {
    /** Where the answers stand. */
    val answers: StateFlow<BirthdayAnswers>

    /** "Accept": puts [birthdate], suggested by message [messageId], on the user's profile. */
    fun accept(
        messageId: Long,
        birthdate: Birthdate,
    )

    /** "Decline": the suggestion of message [messageId] shows as declined, on this device. */
    fun decline(messageId: Long)
}

/**
 * The answers to a conversation's birthday suggestions: the user's own [birthdate] (null without one, or before it is
 * read), who may see it ([privacy]), the suggestions [declined] on this device, and the one being [accepting].
 */
@Immutable
data class BirthdayAnswers(
    val birthdate: Birthdate? = null,
    val privacy: PrivacyChoice? = null,
    val declined: ImmutableSet<Long> = persistentSetOf(),
    val accepting: Long? = null,
) {
    /** Where the answer to the suggestion of message [messageId], of [suggested], stands. */
    fun statusOf(
        messageId: Long,
        suggested: Birthdate,
    ): BirthdaySuggestionStatus =
        when {
            accepting == messageId -> BirthdaySuggestionStatus.Accepting
            birthdate == suggested -> BirthdaySuggestionStatus.Accepted
            messageId in declined -> BirthdaySuggestionStatus.Declined
            else -> BirthdaySuggestionStatus.Pending
        }

    override fun toString() = "BirthdayAnswers(declined=${declined.size}, accepting=$accepting, …)"
}

/** The conversation's birthday suggestions' answers; null in previews and screenshots, where cards wait for one. */
internal val LocalBirthdaySuggestions = staticCompositionLocalOf<BirthdaySuggestionsHost?> { null }
