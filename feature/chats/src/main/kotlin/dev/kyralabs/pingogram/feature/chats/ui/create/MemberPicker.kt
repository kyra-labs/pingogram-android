package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ChatCreationLimits
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.model.isOnlineAt
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.util.Locale

/**
 * Picking people from the contacts for a new group or channel (`REQUIREMENTS.md` FR-P4), a part of the flow's view
 * model ([NewGroupViewModel], [NewChannelViewModel]) running in its [scope], as [state].
 *
 * - **Contacts** come from [ContactRepository.contacts] once the screen collects [state], and follow TDLib's changes
 *   from then on; a failure shows until [MemberPickerEvent.Retry]. They stay while the screen collects, and 5 s after.
 * - **Search**: the query filters the contacts by the words of their names and their usernames as the user types;
 *   after a short pause TDLib's own search ([ContactRepository.search], which finds names in other scripts) adds what
 *   it matches for that query.
 * - **Picks** are kept in the order they were made, at most [ChatCreationLimits.MEMBERS_TO_ADD_MAX]: one more calls
 *   [onTooMany]. A pick whose contact is gone shows no chip, but is still added: they are a Telegram user either way.
 *
 * The query and the picks are kept in the [savedStateHandle] under [keyPrefix], so they survive process death.
 */
@Suppress("LongParameterList") // The flow's scope and saved state, the repository, and what tests replace.
@OptIn(ExperimentalCoroutinesApi::class)
internal class MemberPicker(
    scope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle,
    private val contactRepository: ContactRepository,
    clock: WallClock,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale,
    private val keyPrefix: String,
    private val onTooMany: () -> Unit,
) {
    /** Changes on every retry, which lists the contacts again. */
    private val attempt = MutableStateFlow(0)

    private val queryKey = "$keyPrefix.query"
    private val pickedKey = "$keyPrefix.picked"

    private val query: StateFlow<String> = savedStateHandle.getStateFlow(queryKey, "")

    private val pickedIds: StateFlow<LongArray> = savedStateHandle.getStateFlow(pickedKey, LongArray(0))

    /** The ids of the people picked, in the order they were picked. */
    val picked: Flow<List<Long>> = pickedIds.map { it.toList() }

    /** The ids of the people picked now. */
    val pickedUserIds: List<Long> get() = pickedIds.value.toList()

    /** The contacts: null while they are listed, then what listing them gave, and every change after. */
    private val contacts: Flow<Outcome<ImmutableList<User>>?> =
        attempt.flatMapLatest {
            flow {
                emit(null)
                emitAll(contactRepository.contacts())
            }
        }

    /** What TDLib's search matched, for the query it searched; nothing for a blank one. */
    private val searchMatches: Flow<SearchMatches> =
        query.transformLatest { query ->
            emit(SearchMatches(query, emptySet()))
            if (query.isNotBlank()) {
                // Typing on cancels the wait, so TDLib is asked once the user pauses.
                delay(SEARCH_DELAY_MILLIS)
                val found =
                    contactRepository
                        .search(query)
                        .getOrNull()
                        .orEmpty()
                        .toSet()
                emit(SearchMatches(query, found))
            }
        }

    val state: StateFlow<MemberPickerUiState> =
        combine(contacts, query, searchMatches, pickedIds, clock.minutes, ::toState)
            .flowOn(dispatcher)
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                MemberPickerUiState(query = query.value),
            )

    fun onEvent(event: MemberPickerEvent) {
        when (event) {
            is MemberPickerEvent.QueryChanged -> savedStateHandle[queryKey] = event.query
            is MemberPickerEvent.Toggle -> toggle(event.userId)
            is MemberPickerEvent.Remove -> setPicked(pickedIds.value.filter { it != event.userId })
            MemberPickerEvent.Retry -> if (state.value.contacts is MemberList.Error) attempt.update { it + 1 }
        }
    }

    private fun toggle(userId: Long) {
        val picked = pickedIds.value
        when {
            userId in picked -> setPicked(picked.filter { it != userId })
            picked.size >= ChatCreationLimits.MEMBERS_TO_ADD_MAX -> onTooMany()
            else -> setPicked(picked.toList() + userId)
        }
    }

    private fun setPicked(ids: List<Long>) {
        savedStateHandle[pickedKey] = ids.toLongArray()
    }

    /** Runs on the dispatcher: the contacts filtered by [query], with the picks, as of [now]. */
    private fun toState(
        contacts: Outcome<ImmutableList<User>>?,
        query: String,
        matches: SearchMatches,
        pickedIds: LongArray,
        now: Instant,
    ): MemberPickerUiState {
        val users = (contacts as? Outcome.Success)?.value.orEmpty()
        val picked = pickedIds.toSet()
        val byId = users.associateBy { it.id }
        val pickedItems =
            pickedIds
                .toList()
                .mapNotNull { id -> byId[id]?.toItem(now, isPicked = true) }
                .toImmutableList()
        val list =
            when {
                contacts == null -> {
                    MemberList.Loading
                }

                contacts is Outcome.Failure -> {
                    MemberList.Error(contacts.error)
                }

                users.isEmpty() -> {
                    MemberList.Empty
                }

                else -> {
                    val locale = locale()
                    val tdlibMatches = if (matches.query == query) matches.userIds else emptySet()
                    val shown =
                        if (query.isBlank()) {
                            users
                        } else {
                            users.filter { it.id in tdlibMatches || it.matches(query, locale) }
                        }
                    if (shown.isEmpty()) {
                        MemberList.NoResults
                    } else {
                        MemberList.Content(shown.map { it.toItem(now, it.id in picked) }.toImmutableList())
                    }
                }
            }
        return MemberPickerUiState(query = query, contacts = list, picked = pickedItems)
    }

    /** The contacts TDLib's search matched for [query]. */
    private class SearchMatches(
        val query: String,
        val userIds: Set<Long>,
    )

    companion object {
        /** How long typing pauses before TDLib is asked: the local filter shows matches meanwhile. */
        const val SEARCH_DELAY_MILLIS = 300L

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun User.toItem(
    now: Instant,
    isPicked: Boolean,
) = MemberItem(
    userId = id,
    name = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" "),
    photo = profilePhoto,
    isOnline = type != UserType.Bot && status.isOnlineAt(now.epochSecond),
    username = usernames.firstOrNull(),
    isPicked = isPicked,
)

/**
 * Whether this contact matches [query]: every word of it starts a word of the name, or, without its `@`, starts one
 * of the usernames; in [locale]'s case.
 */
private fun User.matches(
    query: String,
    locale: Locale,
): Boolean {
    val words = query.searchWords(locale)
    val nameWords = "$firstName $lastName".searchWords(locale)
    val username = query.trim().removePrefix("@").lowercase(locale)
    return (words.isNotEmpty() && words.all { word -> nameWords.any { it.startsWith(word) } }) ||
        (username.isNotEmpty() && usernames.any { it.lowercase(locale).startsWith(username) })
}

private fun String.searchWords(locale: Locale): List<String> =
    lowercase(locale).split(WORD_SEPARATORS).filter(String::isNotEmpty)

private val WORD_SEPARATORS = Regex("""[\s\p{Punct}]+""")
