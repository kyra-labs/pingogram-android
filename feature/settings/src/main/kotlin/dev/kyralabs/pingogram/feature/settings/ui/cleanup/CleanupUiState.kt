package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettings
import java.time.Instant

/**
 * The Cleanup suggestions page (`REQUIREMENTS.md` FR-X24): its [settings] once read, and the chat it suggests now
 * ([suggestion]; null when there is none, or while the main list loads, [loading]), with how many more follow it
 * ([remaining]). [isConfirmingLeave] while Leave asks first; [isActing] while a choice is on its way.
 */
@Immutable
data class CleanupUiState(
    val settings: CleanupSettings? = null,
    val suggestion: CleanupSuggestion? = null,
    val remaining: Int = 0,
    val loading: Boolean = true,
    val isConfirmingLeave: Boolean = false,
    val isActing: Boolean = false,
)

/**
 * A suggested chat: its id, its name ([title], the user's own where they have one), photo and [type]; when it was last
 * opened ([lastOpened], or when the phone began keeping it if [neverOpened]); and whether Leave is offered ([canLeave]:
 * a group or channel the user is in and does not own).
 */
@Immutable
data class CleanupSuggestion(
    val chatId: Long,
    val title: String,
    val photo: ProfilePhoto?,
    val type: ChatType,
    val lastOpened: Instant,
    val neverOpened: Boolean,
    val canLeave: Boolean = false,
)

/** What the user does on the page. */
sealed interface CleanupEvent {
    data class SetEnabled(
        val enabled: Boolean,
    ) : CleanupEvent

    data class SetMonths(
        val months: Int,
    ) : CleanupEvent

    /** Mutes the chat for good; it is not suggested again for the months chosen. */
    data object Mute : CleanupEvent

    /** Moves the chat to the archive. */
    data object Archive : CleanupEvent

    /** Asks before leaving the group or channel. */
    data object Leave : CleanupEvent

    data object ConfirmLeave : CleanupEvent

    data object DismissLeave : CleanupEvent

    /** Keeps the chat as it is; it is not suggested again for the months chosen. */
    data object NotNow : CleanupEvent
}

/** One-off side effects of the page, handled once by its route. */
sealed interface CleanupEffect {
    /** A choice or a setting did not go through, shown in a snackbar. */
    data class ShowError(
        val error: DomainError,
    ) : CleanupEffect

    /** A choice went through, said in a snackbar. */
    data class Done(
        val action: CleanupAction,
    ) : CleanupEffect
}

/** The choices that change a chat. */
enum class CleanupAction { Muted, Archived, Left }
