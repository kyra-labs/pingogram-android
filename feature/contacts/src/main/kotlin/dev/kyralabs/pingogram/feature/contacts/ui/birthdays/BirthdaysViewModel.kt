package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.BirthdayReminder
import dev.kyralabs.pingogram.core.domain.model.CloseBirthday
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.BirthdayReminderRepository
import dev.kyralabs.pingogram.core.domain.repository.BirthdayRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/**
 * The Birthdays page (`REQUIREMENTS.md` FR-X18) as [uiState]: the contacts Telegram names as having a birthday these
 * days ([BirthdayRepository.closeBirthdays]), grouped as today's, the upcoming ones and those just past, with their
 * names and photos, the date ("24 November", in the language's order), the age they turn where they share their
 * year, and whether the user set a reminder.
 *
 * - **Remind me** keeps one reminder per contact on the phone ([BirthdayReminderRepository]), set and cleared only
 *   here; one that cannot be saved is said once ([BirthdaysEffect.ReminderFailed]).
 * - **Add to calendar** hands the calendar app a yearly event, from the birthday's next date.
 * - A row opens the chat with the contact ([BirthdaysEffect.OpenChat]).
 *
 * Tests replace the clock, time patterns, dispatcher and language ([locale]).
 */
@HiltViewModel
class BirthdaysViewModel internal constructor(
    private val birthdayRepository: BirthdayRepository,
    private val reminderRepository: BirthdayReminderRepository,
    userRepository: UserRepository,
    private val clock: WallClock,
    private val timePatterns: TimePatterns,
    dispatcher: CoroutineDispatcher,
    private val locale: () -> Locale = Locale::getDefault,
) : ViewModel() {
    @Inject
    constructor(
        birthdayRepository: BirthdayRepository,
        reminderRepository: BirthdayReminderRepository,
        userRepository: UserRepository,
        clock: WallClock,
        timePatterns: TimePatterns,
        @Dispatcher(Default) dispatcher: CoroutineDispatcher,
    ) : this(
        birthdayRepository = birthdayRepository,
        reminderRepository = reminderRepository,
        userRepository = userRepository,
        clock = clock,
        timePatterns = timePatterns,
        dispatcher = dispatcher,
        locale = Locale::getDefault,
    )

    private val effectChannel = Channel<BirthdaysEffect>(Channel.BUFFERED)

    /** One-off effects for the page: opening a chat, adding to the calendar, a reminder not saved. */
    val effects: Flow<BirthdaysEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<BirthdaysUiState> =
        combine(
            birthdayRepository.closeBirthdays,
            reminderRepository.reminders,
            userRepository.profiles,
            clock.minutes,
        ) { birthdays, reminders, users, now ->
            toUiState(birthdays, reminders.mapTo(HashSet()) { it.userId }, users, now)
        }.flowOn(dispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BirthdaysUiState.Loading)

    fun onEvent(event: BirthdaysEvent) {
        when (event) {
            is BirthdaysEvent.Open -> effectChannel.trySend(BirthdaysEffect.OpenChat(event.userId))
            is BirthdaysEvent.SetReminder -> setReminder(event.userId, event.on)
            is BirthdaysEvent.AddToCalendar -> addToCalendar(event.userId)
        }
    }

    private fun birthdayOf(userId: Long): CloseBirthday? =
        birthdayRepository.closeBirthdays.value.firstOrNull { it.userId == userId }

    private fun setReminder(
        userId: Long,
        on: Boolean,
    ) {
        val birthday = birthdayOf(userId) ?: return
        viewModelScope.launch {
            val date = birthday.birthdate
            val saved =
                if (on) {
                    reminderRepository.set(BirthdayReminder(userId, date.day, date.month))
                } else {
                    reminderRepository.delete(userId)
                }
            if (saved is Outcome.Failure) effectChannel.trySend(BirthdaysEffect.ReminderFailed(saved.error))
        }
    }

    private fun addToCalendar(userId: Long) {
        val birthday = birthdayOf(userId) ?: return
        val name = (uiState.value as? BirthdaysUiState.Content)?.itemOf(userId)?.name ?: return
        val today = clock.now().atZone(clock.zone()).toLocalDate()
        val monthDay = MonthDay.of(birthday.birthdate.month, birthday.birthdate.day)
        val next = monthDay.atYear(today.year).takeUnless { it.isBefore(today) } ?: monthDay.atYear(today.year + 1)
        effectChannel.trySend(BirthdaysEffect.AddToCalendar(name, next.year, next.monthValue, next.dayOfMonth))
    }

    /** Runs on the dispatcher: [birthdays] grouped around today, contacts TDLib has not named yet left out. */
    private fun toUiState(
        birthdays: List<CloseBirthday>,
        reminded: Set<Long>,
        users: Map<Long, User>,
        now: Instant,
    ): BirthdaysUiState {
        val today = now.atZone(clock.zone()).toLocalDate()
        val formatter = DateTimeFormatter.ofPattern(timePatterns.bestPattern(locale(), DATE_SKELETON), locale())
        val dated =
            birthdays.mapNotNull { birthday ->
                val user = users[birthday.userId] ?: return@mapNotNull null
                val date = nearestDate(birthday, today)
                val item =
                    BirthdayItem(
                        userId = birthday.userId,
                        name = user.fullName(),
                        photo = user.profilePhoto,
                        date = formatter.format(date),
                        turns = birthday.birthdate.year?.let { date.year - it },
                        remind = birthday.userId in reminded,
                    )
                ChronoUnit.DAYS.between(today, date) to item
            }
        if (dated.isEmpty()) return BirthdaysUiState.Empty
        return BirthdaysUiState.Content(
            today = dated.filter { it.first == 0L }.map { it.second }.toImmutableList(),
            upcoming =
                dated
                    .filter { it.first > 0 }
                    .sortedBy { it.first }
                    .map { it.second }
                    .toImmutableList(),
            past =
                dated
                    .filter { it.first < 0 }
                    .sortedByDescending { it.first }
                    .map { it.second }
                    .toImmutableList(),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** The day and the month's name, in the language's order: "24 November", "November 24". */
        const val DATE_SKELETON = "MMMMd"
    }
}

/** The date of [birthday] nearest to [today]: this year's, last year's or next year's. */
internal fun nearestDate(
    birthday: CloseBirthday,
    today: LocalDate,
): LocalDate {
    val monthDay = MonthDay.of(birthday.birthdate.month, birthday.birthdate.day)
    return (today.year - 1..today.year + 1)
        .map(monthDay::atYear)
        .minBy { abs(ChronoUnit.DAYS.between(today, it)) }
}

private fun BirthdaysUiState.Content.itemOf(userId: Long): BirthdayItem? =
    (today + upcoming + past).firstOrNull { it.userId == userId }
