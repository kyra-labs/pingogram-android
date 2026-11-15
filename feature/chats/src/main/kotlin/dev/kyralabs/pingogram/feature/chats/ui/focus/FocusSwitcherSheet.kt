package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.FocusBadge
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Focus switcher (`docs/DESIGN_SYSTEM.md` §13.13, board 31), from the Chats header's pill: a sheet with "Focus" in
 * `titleLargeEmphasized` and "Manage" ([onManage], Settings → Focus); the profiles and Off as one radio group of
 * segmented rows, each its symbol on its shape and what it does now ("Until 6:00 PM (schedule)", "Starts at 10:30
 * PM", "By hand"), the one on on `primaryContainer`; then "Keep it on": until the schedule's next change (where there
 * is one), an hour, or until turned off, as filter chips, the chosen one a `primary` pill. A choice applies at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusSwitcherSheet(
    state: FocusSwitchUiState,
    onEvent: (FocusSwitchEvent) -> Unit,
    onManage: () -> Unit,
) {
    PingogramBottomSheet(onDismissRequest = { onEvent(FocusSwitchEvent.Close) }) {
        FocusSwitcherContent(state, onEvent, onManage)
    }
}

/** What [FocusSwitcherSheet] holds, apart so tests and previews draw it without the sheet's window. */
@Composable
internal fun FocusSwitcherContent(
    state: FocusSwitchUiState,
    onEvent: (FocusSwitchEvent) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.large, end = spacing.large, bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.chat_list_focus_title),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            FilledTonalButton(onClick = onManage, shapes = ButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_tune),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = stringResource(R.string.chat_list_focus_manage),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
        FocusChoices(state, onEvent)
        Text(
            text = stringResource(R.string.chat_list_focus_keep),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        KeepChips(state, onEvent)
    }
}

/** The profiles, then Off, as one radio group; a choice applies at once, with a `SegmentTick` haptic. */
@Composable
private fun FocusChoices(
    state: FocusSwitchUiState,
    onEvent: (FocusSwitchEvent) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val count = state.profiles.size + 1
    val noneOn = state.profiles.none { it.isOn }
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        state.profiles.forEachIndexed { index, profile ->
            ChoiceRow(
                selected = profile.isOn,
                index = index,
                count = count,
                title = profile.name,
                isUserContent = true,
                summary = statusText(profile.status),
                leading = { on -> ProfileBadge(profile, on) },
                onClick = {
                    if (!profile.isOn) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onEvent(FocusSwitchEvent.Choose(profile.id))
                },
            )
        }
        ChoiceRow(
            selected = noneOn,
            index = count - 1,
            count = count,
            title = stringResource(R.string.chat_list_focus_off),
            isUserContent = false,
            summary = stringResource(R.string.chat_list_focus_off_summary),
            leading = { on -> OffBadge(on) },
            onClick = {
                if (!noneOn) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onEvent(FocusSwitchEvent.Choose(null))
            },
        )
    }
}

/** A choice's row: segmented, on `primaryContainer` while chosen, its radio button at the end for screen readers. */
@Composable
private fun ChoiceRow(
    selected: Boolean,
    index: Int,
    count: Int,
    title: String,
    isUserContent: Boolean,
    summary: String,
    leading: @Composable (selected: Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors =
            ListItemDefaults.segmentedColors(
                selectedContainerColor = colors.primaryContainer,
                selectedContentColor = colors.onPrimaryContainer,
                selectedSupportingContentColor = colors.onPrimaryContainer,
            ),
        modifier = Modifier.semantics { role = Role.RadioButton },
        leadingContent = { leading(selected) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = { Text(summary) },
    ) {
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.let { if (isUserContent) it.userContent else it },
        )
    }
}

@Composable
private fun ProfileBadge(
    profile: FocusSwitchProfile,
    on: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    FocusBadge(
        icon = profile.icon,
        size = MaterialTheme.sizes.leadingIconContainer,
        container = if (on) colors.onPrimaryContainer else colors.secondaryContainer,
        content = if (on) colors.primaryContainer else colors.onSecondaryContainer,
    )
}

/** Off's symbol in a circle, as the rows' badges sit. */
@Composable
private fun OffBadge(on: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.leadingIconContainer)
                .background(if (on) colors.onPrimaryContainer else colors.surfaceContainerHighest, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_notifications),
            contentDescription = null,
            tint = if (on) colors.primaryContainer else colors.onSurfaceVariant,
        )
    }
}

/** "Keep it on": filter chips in one radio group; the chosen one a `primary` pill, the others outlined. */
@Composable
private fun KeepChips(
    state: FocusSwitchUiState,
    onEvent: (FocusSwitchEvent) -> Unit,
) {
    val time = rememberFocusTime()
    val haptics = LocalHapticFeedback.current
    val options =
        buildList {
            state.scheduleChangeAt?.let {
                add(FocusKeep.ScheduleChange to stringResource(R.string.chat_list_focus_keep_until, time(it)))
            }
            add(FocusKeep.OneHour to stringResource(R.string.chat_list_focus_keep_hour))
            add(FocusKeep.TurnedOff to stringResource(R.string.chat_list_focus_keep_off))
        }
    // Without a schedule change to wait for, a choice lasts until turned off.
    val chosen =
        state.keep.takeIf { it != FocusKeep.ScheduleChange || state.scheduleChangeAt != null } ?: FocusKeep.TurnedOff
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        options.forEach { (keep, label) ->
            val selected = keep == chosen
            FilterChip(
                selected = selected,
                onClick = {
                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onEvent(FocusSwitchEvent.Keep(keep))
                },
                label = { Text(label) },
                modifier =
                    Modifier.semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    },
                shapes = FilterChipDefaults.shapes(),
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = colors.onPrimary,
                    ),
            )
        }
    }
}

/** What a profile's row says under its name. */
@Composable
private fun statusText(status: FocusProfileStatus): String {
    val time = rememberFocusTime()
    return when (status) {
        is FocusProfileStatus.UntilScheduleEnds -> {
            stringResource(
                R.string.chat_list_focus_until_schedule,
                time(status.at),
            )
        }

        is FocusProfileStatus.Until -> {
            stringResource(R.string.chat_list_focus_until, time(status.at))
        }

        FocusProfileStatus.UntilTurnedOff -> {
            stringResource(R.string.chat_list_focus_until_off)
        }

        is FocusProfileStatus.StartsAt -> {
            stringResource(R.string.chat_list_focus_starts, time(status.at))
        }

        FocusProfileStatus.ByHand -> {
            stringResource(R.string.chat_list_focus_by_hand)
        }
    }
}

/** [FocusSwitcherSheet] while the switcher is open; [state] is read here alone. */
@Composable
internal fun FocusSwitcherHost(
    state: State<FocusSwitchUiState>,
    onEvent: (FocusSwitchEvent) -> Unit,
    onManage: () -> Unit,
) {
    val open by remember(state) { derivedStateOf { state.value.switcherOpen } }
    if (open) {
        FocusSwitcherSheet(
            state = state.value,
            onEvent = onEvent,
            onManage = {
                onEvent(FocusSwitchEvent.Close)
                onManage()
            },
        )
    }
}

/** Shows why a Focus change was not saved, in the chat list's snackbar. */
@Composable
internal fun FocusSwitchEffects(
    effects: Flow<FocusSwitchEffect>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is FocusSwitchEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
}
