package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** The chat list's Focus as previews and goldens show it: 18:00 and 22:30 on 2026-11-16, in UTC. */
internal object FocusPreviewData {
    private const val SIX_PM = 1_794_852_000L
    private const val HALF_PAST_TEN = 1_794_868_200L

    val pill = FocusPill("Work hours", FocusIcon.Work, SIX_PM)

    val workOn =
        FocusSwitchUiState(
            pill = pill,
            switcherOpen = true,
            profiles =
                persistentListOf(
                    FocusSwitchProfile(1, "Work hours", FocusIcon.Work, true, FocusProfileStatus.UntilScheduleEnds(SIX_PM)),
                    FocusSwitchProfile(2, "Sleep", FocusIcon.Sleep, false, FocusProfileStatus.StartsAt(HALF_PAST_TEN)),
                    FocusSwitchProfile(3, "Weekend", FocusIcon.Weekend, false, FocusProfileStatus.ByHand),
                ),
            keep = FocusKeep.ScheduleChange,
            scheduleChangeAt = SIX_PM,
        )

    val off =
        workOn.copy(
            pill = null,
            profiles =
                persistentListOf(
                    workOn.profiles[0].copy(isOn = false, status = FocusProfileStatus.ByHand),
                    workOn.profiles[1],
                    workOn.profiles[2],
                ),
            keep = FocusKeep.TurnedOff,
            scheduleChangeAt = null,
        )
}

internal class FocusSwitcherPreviewStates : PreviewParameterProvider<FocusSwitchUiState> {
    override val values = sequenceOf(FocusPreviewData.workOn, FocusPreviewData.off)
}

@PingogramPreviews
@Composable
private fun FocusPillPreview() {
    PingogramPreview { FocusPillChip(FocusPreviewData.pill, onOpen = {}) }
}

@PingogramPreviews
@Composable
private fun FocusSwitcherPreview(
    @PreviewParameter(FocusSwitcherPreviewStates::class) state: FocusSwitchUiState,
) {
    PingogramPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            FocusSwitcherContent(state, onEvent = {}, onManage = {})
        }
    }
}
