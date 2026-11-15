package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import java.time.DayOfWeek

// Focus profiles (board 31, owner decisions 2026-11-15): kept on this phone only, never sent to Telegram. One profile
// is on at a time; a profile turned on by hand overrides the schedule until the schedule's next change. Focus changes
// notifications only: the chat list stays as it is.

/**
 * A Focus profile: while it is on, only the chats it allows alert; the others' notifications show silently.
 *
 * @property id the profile's id on this phone, positive; 0 for one not saved yet.
 * @property name what the user calls it ("Work hours").
 * @property icon its symbol and shape.
 * @property allowedFolderIds the chat folders (TDLib's `chat_folder_id`) whose chats still alert.
 * @property allowedChatIds chats that still alert, whatever folder they are in.
 * @property schedule when it turns on by itself; null for one turned on by hand only.
 * @property allowsCallsFromContacts whether calls from contacts still ring; everyone else's go to missed calls.
 */
data class FocusProfile(
    val id: Long,
    val name: String,
    val icon: FocusIcon,
    val allowedFolderIds: ImmutableList<Int>,
    val allowedChatIds: ImmutableList<Long>,
    val schedule: FocusSchedule?,
    val allowsCallsFromContacts: Boolean,
) {
    override fun toString() = "FocusProfile(id=$id, icon=$icon, schedule=$schedule, …)"
}

/** A profile's symbol, each with its shape (board 31). */
enum class FocusIcon {
    Work,
    Sleep,
    Weekend,
    Reading,
    Fitness,
    Driving,
}

/**
 * When a profile turns on by itself: on [days], from [startMinute] to [endMinute] (minutes after midnight, local time).
 * A range across midnight is one range (owner decision 2026-11-15: "22:30–7:00, next day"): an [endMinute] at or
 * before [startMinute] ends on the next day, and [days] are the days it starts on.
 */
data class FocusSchedule(
    val days: ImmutableSet<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
) {
    /** Whether it ends on the day after it starts. */
    val endsNextDay: Boolean get() = endMinute <= startMinute
}

/**
 * Focus as the user set it by hand: [profileId] on (null: Focus off), set at [setAt] (Unix seconds), lasting
 * [until]. It overrides the schedule until it ends.
 */
data class ManualFocus(
    val profileId: Long?,
    val setAt: Long,
    val until: FocusUntil,
)

/** How long Focus set by hand lasts ("Keep it on"). */
sealed interface FocusUntil {
    /** Until the schedule next changes (owner decision 2026-11-15), or for good where no profile has a schedule. */
    data object ScheduleChange : FocusUntil

    /** Until [epochSeconds]: "1 hour". */
    data class Time(
        val epochSeconds: Long,
    ) : FocusUntil

    /** Until the user turns it off. */
    data object TurnedOff : FocusUntil
}

/**
 * The profile on now ([profile]), whether the user turned it on by hand ([byHand]), and when it ends ([endsAt], Unix
 * seconds; null when it lasts until turned off).
 */
data class ActiveFocus(
    val profile: FocusProfile,
    val byHand: Boolean,
    val endsAt: Long?,
)
