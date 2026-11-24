package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.repository.BirthdayRepository
import dev.kyralabs.pingogram.core.domain.repository.BirthdaysRowRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.abs

/**
 * The Contacts tab's "Birthdays" row (`REQUIREMENTS.md` FR-X18): who has a birthday these days, nearest first, while
 * Telegram names any whose name is known and Privacy and security → Contacts → "Birthdays" is on ([entry], else
 * null). The tab's own view model is left as it is: the row is a small state of its own.
 */
@HiltViewModel
class BirthdaysEntryViewModel
    @Inject
    constructor(
        birthdayRepository: BirthdayRepository,
        rowRepository: BirthdaysRowRepository,
        userRepository: UserRepository,
        clock: WallClock,
    ) : ViewModel() {
        val entry: StateFlow<BirthdaysEntry?> =
            combine(
                birthdayRepository.closeBirthdays,
                rowRepository.enabled,
                userRepository.profiles,
                clock.minutes,
            ) { birthdays, enabled, users, now ->
                if (!enabled) return@combine null
                val today = now.atZone(clock.zone()).toLocalDate()
                val names =
                    birthdays
                        .mapNotNull { birthday ->
                            val user = users[birthday.userId] ?: return@mapNotNull null
                            val days = ChronoUnit.DAYS.between(today, nearestDate(birthday, today))
                            // Today first, then the upcoming ones, then those just past.
                            val order = if (days >= 0) days else abs(days) + PAST_AFTER
                            order to user.fullName()
                        }.sortedBy { it.first }
                        .map { it.second }
                if (names.isEmpty()) null else BirthdaysEntry(names.take(SHOWN).toImmutableList(), names.size - SHOWN)
            }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** The names the row shows; the others are counted. */
            const val SHOWN = 2

            /** Puts the birthdays just past after every upcoming one of the few days Telegram names. */
            const val PAST_AFTER = 1_000L
        }
    }

/** The row's [names], nearest birthday first, and how many [more] have one; [more] is 0 or less with none left. */
@Immutable
data class BirthdaysEntry(
    val names: ImmutableList<UiText>,
    val more: Int,
)
