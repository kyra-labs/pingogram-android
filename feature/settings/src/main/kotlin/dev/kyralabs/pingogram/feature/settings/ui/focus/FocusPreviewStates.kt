package dev.kyralabs.pingogram.feature.settings.ui.focus

import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.domain.model.FocusSchedule
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import java.time.DayOfWeek

/** The states of the Focus page and its editor, shared by previews and screenshot tests. */
internal object FocusPreviewStates {
    private val weekdays =
        persistentSetOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

    val content =
        FocusUiState(
            persistentListOf(
                FocusProfileItem(
                    id = 1,
                    name = "Work hours",
                    icon = FocusIcon.Work,
                    schedule = FocusSchedule(weekdays, 9 * 60, 18 * 60),
                    folderCount = 2,
                    chatCount = 3,
                    allowsCalls = true,
                    isOn = true,
                ),
                FocusProfileItem(
                    id = 2,
                    name = "Sleep",
                    icon = FocusIcon.Sleep,
                    schedule = FocusSchedule(DayOfWeek.entries.toPersistentSet(), 22 * 60 + 30, 7 * 60),
                    folderCount = 0,
                    chatCount = 1,
                    allowsCalls = true,
                    isOn = false,
                ),
                FocusProfileItem(
                    id = 3,
                    name = "Weekend",
                    icon = FocusIcon.Weekend,
                    schedule = null,
                    folderCount = 0,
                    chatCount = 0,
                    allowsCalls = false,
                    isOn = false,
                ),
            ),
        )

    val empty = FocusUiState(persistentListOf())

    val editing =
        FocusEditUiState(
            draft =
                FocusDraft(
                    name = "Work hours",
                    icon = FocusIcon.Work,
                    folderIds = persistentSetOf(1),
                    chatIds = persistentListOf(10L, 11L),
                    scheduled = true,
                    days = weekdays,
                    startMinute = 9 * 60,
                    endMinute = 18 * 60,
                    allowsCalls = true,
                ),
            folders =
                persistentListOf(
                    FocusFolderChoice(1, "Work"),
                    FocusFolderChoice(2, "Family"),
                    FocusFolderChoice(3, "Unread"),
                ),
            allowedChats =
                persistentListOf(
                    FocusChatChoice(10, "Ada Lovelace", null),
                    FocusChatChoice(11, "Release team", null),
                ),
            isNew = false,
        )

    val newByHand =
        editing.copy(
            draft = editing.draft?.copy(name = "", scheduled = false),
            folders = persistentListOf(),
            allowedChats = persistentListOf(),
            isNew = true,
            nameMissing = true,
        )

    val askDelete = editing.copy(askDelete = true)

    val chatPicker =
        FocusChatPickerUiState(
            query = "",
            candidates =
                persistentListOf(
                    FocusChatCandidate(FocusChatChoice(12, "Alan Turing", null), selected = true),
                    FocusChatCandidate(FocusChatChoice(13, "Grace Hopper", null), selected = false),
                    FocusChatCandidate(FocusChatChoice(14, "Book club", null), selected = false),
                ),
        )
}
