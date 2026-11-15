package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import java.time.DayOfWeek

/**
 * A Focus profile being edited: its [name] and [icon], the folders it lets through ([folderIds]) and its chats
 * ([chatIds], kept as they are here), whether it runs on a [scheduled] range of [days] from [startMinute] to
 * [endMinute] (minutes after midnight; an end at or before the start is on the next day) or by hand, and whether calls
 * from contacts ring ([allowsCalls]).
 */
@Immutable
data class FocusDraft(
    val name: String,
    val icon: FocusIcon,
    val folderIds: ImmutableSet<Int>,
    val chatIds: ImmutableList<Long>,
    val scheduled: Boolean,
    val days: ImmutableSet<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val allowsCalls: Boolean,
) {
    override fun toString() = "FocusDraft(icon=$icon, scheduled=$scheduled, …)"
}

/** A chat folder the profile may let through: its [id] and [title]. */
@Immutable
data class FocusFolderChoice(
    val id: Int,
    val title: String,
) {
    override fun toString() = "FocusFolderChoice(id=$id, …)"
}

/** A chat "Also allow" lets through: its [id], [title] and [photo]. */
@Immutable
data class FocusChatChoice(
    val id: Long,
    val title: String,
    val photo: ProfilePhoto?,
) {
    override fun toString() = "FocusChatChoice(id=$id, …)"
}

/** A chat the picker offers: [chat], picked while [selected]. */
@Immutable
data class FocusChatCandidate(
    val chat: FocusChatChoice,
    val selected: Boolean,
)

/** The picker of chats to also allow: its [query] and the main list's other chats it finds ([candidates]). */
@Immutable
data class FocusChatPickerUiState(
    val query: String,
    val candidates: ImmutableList<FocusChatCandidate>,
) {
    override fun toString() = "FocusChatPickerUiState(candidates=${candidates.size})"
}

/** Which of a schedule's two times the time picker sets. */
enum class FocusTimeEnd { Start, End }

/**
 * The profile editor: the [draft] (null until the profile is read), the user's chat [folders], the chats it also
 * allows as TDLib names them ([allowedChats], those it has not announced left out), whether it is a new
 * profile ([isNew]), whether it is being saved or deleted ([isBusy]), whether "Save" was refused for a missing name
 * ([nameMissing]), the time picker open on one end ([picking]) and whether deleting is being asked ([askDelete]).
 */
@Immutable
data class FocusEditUiState(
    val draft: FocusDraft? = null,
    val folders: ImmutableList<FocusFolderChoice>? = null,
    val allowedChats: ImmutableList<FocusChatChoice> = persistentListOf(),
    val isNew: Boolean = true,
    val isBusy: Boolean = false,
    val nameMissing: Boolean = false,
    val picking: FocusTimeEnd? = null,
    val askDelete: Boolean = false,
)

/** What the user does in the profile editor, handled by [FocusEditViewModel.onEvent]. */
sealed interface FocusEditEvent {
    /** A template's title in the app's language, the name of a new profile started from it (once). */
    data class Prefill(
        val name: String,
    ) : FocusEditEvent

    data class Name(
        val name: String,
    ) : FocusEditEvent

    data class Icon(
        val icon: FocusIcon,
    ) : FocusEditEvent

    data class Folder(
        val id: Int,
        val allowed: Boolean,
    ) : FocusEditEvent

    data class Scheduled(
        val scheduled: Boolean,
    ) : FocusEditEvent

    data class Day(
        val day: DayOfWeek,
        val on: Boolean,
    ) : FocusEditEvent

    data class Pick(
        val end: FocusTimeEnd?,
    ) : FocusEditEvent

    data class Time(
        val end: FocusTimeEnd,
        val minute: Int,
    ) : FocusEditEvent

    data class Calls(
        val allowed: Boolean,
    ) : FocusEditEvent

    data object Save : FocusEditEvent

    data object Delete : FocusEditEvent

    data object DeleteConfirmed : FocusEditEvent

    data object DeleteDismissed : FocusEditEvent
}

/** What the user does with "Also allow" and its picker, handled by [FocusAllowedChats]. */
sealed interface FocusChatEvent : FocusEditEvent {
    /** "Add chats": opens the picker. */
    data object Add : FocusChatEvent

    data class Remove(
        val chatId: Long,
    ) : FocusChatEvent

    data class Query(
        val query: String,
    ) : FocusChatEvent

    data class Toggle(
        val chatId: Long,
    ) : FocusChatEvent

    /** "Done", or the sheet dismissed: the picks join the allowed chats. */
    data object Close : FocusChatEvent
}

/** One-off side effects of [FocusEditViewModel], handled once by its route. */
sealed interface FocusEditEffect {
    /** Saved or deleted: the editor closes. */
    data object Done : FocusEditEffect

    /** "Save" refused: no name, or no day on a schedule. */
    data object Rejected : FocusEditEffect

    data class ShowError(
        val error: DomainError,
    ) : FocusEditEffect
}
