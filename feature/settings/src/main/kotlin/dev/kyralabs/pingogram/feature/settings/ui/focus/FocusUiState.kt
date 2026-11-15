package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import kotlinx.collections.immutable.ImmutableList

/**
 * A Focus profile as the Focus page lists it: its [name] and [icon], how it runs ([schedule], or by hand without one),
 * what it lets through ([folderCount] folders, [chatCount] chats, calls from contacts with [allowsCalls]), and whether
 * it is on now ([isOn]).
 */
@Immutable
data class FocusProfileItem(
    val id: Long,
    val name: String,
    val icon: FocusIcon,
    val schedule: FocusSchedule?,
    val folderCount: Int,
    val chatCount: Int,
    val allowsCalls: Boolean,
    val isOn: Boolean,
) {
    override fun toString() = "FocusProfileItem(id=$id, icon=$icon, isOn=$isOn, …)"
}

/** The Focus page: its [profiles] in the user's order, or null until they are read (a matter of milliseconds). */
@Immutable
data class FocusUiState(
    val profiles: ImmutableList<FocusProfileItem>? = null,
)

/** What the user does on the Focus page, handled by [FocusViewModel.onEvent]. */
sealed interface FocusEvent {
    /** Turns profile [id] on by hand ([on]), until the schedule's next change, or Focus off until then. */
    data class Toggle(
        val id: Long,
        val on: Boolean,
    ) : FocusEvent
}

/** One-off side effects of [FocusViewModel], handled once by its route. */
sealed interface FocusEffect {
    data class ShowError(
        val error: DomainError,
    ) : FocusEffect
}

/** A profile the page offers to start from, filled in for the editor (board 31). */
enum class FocusTemplate {
    WorkHours,
    Sleep,
    Weekend,
    Blank,
}
