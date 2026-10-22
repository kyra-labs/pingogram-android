package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.SlowModeDelays
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSectionHeader
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import dev.kyralabs.pingogram.feature.profile.ui.profileRowContainerColor
import kotlin.math.roundToInt

/** The permissions page of a group: hoists its [ChatPermissionsViewModel]; errors are snackbars. */
@Composable
internal fun ChatPermissionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatPermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatPermissionsEffect.ShowError -> {
                    snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showDetails))
                }
            }
        }
    }
    ChatPermissionsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * The permissions page (`docs/DESIGN_SYSTEM.md` §6 "Group and channel info"), a settings-style page: "What members
 * can do" as a group of switch rows ([RightsGroup]), then, in supergroups, "Slow mode" as a segment holding a nested
 * list item with the delay ("Every 30 seconds") and an expressive `Slider` through Telegram's delays with a
 * `SegmentTick` per step, sent when let go. Each change shows at once; a wavy line shows while Telegram answers.
 */
@Composable
internal fun ChatPermissionsScreen(
    state: ChatPermissionsUiState,
    onEvent: (ChatPermissionsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    InfoPageScaffold(
        title = stringResource(R.string.chat_info_permissions),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        when (state) {
            ChatPermissionsUiState.Loading -> {
                PageLoading(innerPadding)
            }

            is ChatPermissionsUiState.Error -> {
                InfoPageBody(innerPadding) { PageError(state.error) { onEvent(ChatPermissionsEvent.Retry) } }
            }

            is ChatPermissionsUiState.Content -> {
                InfoPageContent(innerPadding) {
                    if (state.isSaving) SavingIndicator()
                    RightsGroup(
                        title = stringResource(R.string.chat_info_permissions_header),
                        items = state.switches,
                        onToggle = { key, on -> onEvent(ChatPermissionsEvent.Toggle(key, on)) },
                    )
                    state.slowModeDelay?.let { delay ->
                        SlowModeSegment(
                            delay,
                            enabled = !state.isSaving,
                        ) { onEvent(ChatPermissionsEvent.SetSlowMode(it)) }
                    }
                }
            }
        }
    }
}

/**
 * The slow mode: a heading, then one segment with the delay chosen in a nested list item and a `Slider` through
 * [SlowModeDelays]. The thumb follows the finger with a `SegmentTick` at each step; letting go sends the delay
 * ([onChange]). Screen readers hear "Slow mode" with the delay as its state.
 */
@Composable
private fun SlowModeSegment(
    delay: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val sliderState =
        remember(delay) {
            // The value, the steps between the ends, and the range: one step per delay.
            SliderState(
                SlowModeDelays.indexOf(delay).coerceAtLeast(0).toFloat(),
                SlowModeDelays.size - 2,
                0f..SlowModeDelays.lastIndex.toFloat(),
            )
        }
    val step = sliderState.value.roundToInt().coerceIn(SlowModeDelays.indices)
    val title = stringResource(R.string.chat_info_slow_mode)
    val delayText = slowModeText(SlowModeDelays[step])
    Column {
        ProfileSectionHeader(title)
        Surface(shape = ListItemDefaults.segmentedShapes(0, 1).shape, color = profileRowContainerColor()) {
            Column {
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    colors = profileRowColors(),
                    supportingContent = { Text(stringResource(R.string.chat_info_slow_mode_summary)) },
                ) {
                    Text(delayText)
                }
                Slider(
                    state = sliderState,
                    onValueChange = { value ->
                        val next = value.roundToInt().coerceIn(SlowModeDelays.indices)
                        if (next != step) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        sliderState.value = value
                    },
                    onValueChangeFinished = { onChange(SlowModeDelays[step]) },
                    modifier =
                        Modifier
                            .padding(
                                start = MaterialTheme.spacing.large,
                                end = MaterialTheme.spacing.large,
                                bottom = MaterialTheme.spacing.small,
                            ).semantics {
                                contentDescription = title
                                stateDescription = delayText
                            },
                    enabled = enabled,
                )
            }
        }
    }
}

/** "Off", "Every 30 seconds", "Every 5 minutes", "Every hour". */
@Composable
private fun slowModeText(seconds: Int): String =
    when {
        seconds == 0 -> {
            stringResource(R.string.chat_info_slow_mode_off)
        }

        seconds < SECONDS_PER_MINUTE -> {
            pluralStringResource(R.plurals.chat_info_slow_mode_seconds, seconds, seconds)
        }

        seconds < SECONDS_PER_HOUR -> {
            val minutes = seconds / SECONDS_PER_MINUTE
            pluralStringResource(R.plurals.chat_info_slow_mode_minutes, minutes, minutes)
        }

        else -> {
            val hours = seconds / SECONDS_PER_HOUR
            pluralStringResource(R.plurals.chat_info_slow_mode_hours, hours, hours)
        }
    }

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
