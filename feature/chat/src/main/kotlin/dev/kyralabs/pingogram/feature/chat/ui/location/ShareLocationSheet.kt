package dev.kyralabs.pingogram.feature.chat.ui.location

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ScheduleDialog
import dev.kyralabs.pingogram.feature.chat.ui.composer.SendButton
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The "Location" sheet of the attach menu (`REQUIREMENTS.md` FR-C5; `docs/DESIGN_SYSTEM.md` §6 "Share location"): a
 * [ModalBottomSheet] that finds the device's location and sends it. No map: a map needs a map service, and Pingogram
 * talks to nothing but Telegram (`REQUIREMENTS.md` §6.2). It shows the coordinates and their accuracy instead, and
 * "Open in maps" shows them in the user's maps app where there is one ([canOpenMaps]).
 *
 * - **Permission** ([ShareLocationUiState.Permission]): why Pingogram asks, then Android asks
 *   ([ShareLocationEvent.Allow]); once Android no longer asks, the button opens the app's settings.
 * - **Searching** ([ShareLocationUiState.Locating] without a location): a loading indicator once it takes a moment.
 * - **Found**: the coordinates and their accuracy under a `MaterialShapes` badge, a wavy indicator while a better
 *   location is still likely ([ShareLocationUiState.Locating.isRefining]), and for an approximate location a note
 *   with "Use precise location". The send split button sends now, without sound, or at a time where [canSchedule]:
 *   it confirms with a haptic, hides the sheet on its spring, then [onSend] gets the location.
 * - **Not found** ([ShareLocationUiState.NotFound]): "Try again".
 *
 * Steps crossfade on the effects springs while the sheet resizes on the default spatial spring. Dragging the sheet
 * down, the scrim and the back gesture (Material's predictive back) dismiss it ([ShareLocationEvent.Dismiss]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareLocationSheet(
    state: ShareLocationUiState,
    canSchedule: Boolean,
    canOpenMaps: Boolean,
    onEvent: (ShareLocationEvent) -> Unit,
    onSend: (DeviceLocation, SendOptions) -> Unit,
    now: () -> Instant = Instant::now,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnSend by rememberUpdatedState(onSend)
    val send: (DeviceLocation, SendOptions) -> Unit =
        remember(sheetState, scope, haptics) {
            { location, options ->
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnSend(location, options) }
            }
        }
    var scheduling by rememberSaveable { mutableStateOf(false) }
    val located = (state as? ShareLocationUiState.Locating)?.location
    ModalBottomSheet(onDismissRequest = { onEvent(ShareLocationEvent.Dismiss) }, sheetState = sheetState) {
        ShareLocationContent(
            state = state,
            canSchedule = canSchedule,
            canOpenMaps = canOpenMaps,
            onEvent = onEvent,
            onSend = send,
            onSchedule = { scheduling = true },
        )
    }
    if (scheduling && located != null) {
        ScheduleDialog(
            now = now,
            onDismiss = { scheduling = false },
            onSchedule = { at ->
                scheduling = false
                send(located, SendOptions(scheduledAt = at.epochSecond))
            },
        )
    }
}

/** The sheet's title and its step, scrolling when they do not fit (landscape, large font scales). */
@Suppress("LongParameterList") // The step, what the send menu offers, and what its buttons do.
@Composable
internal fun ShareLocationContent(
    state: ShareLocationUiState,
    canSchedule: Boolean,
    canOpenMaps: Boolean,
    onEvent: (ShareLocationEvent) -> Unit,
    onSend: (DeviceLocation, SendOptions) -> Unit,
    onSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val motion = MaterialTheme.motionScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.large),
    ) {
        Text(
            text = stringResource(R.string.location_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            contentKey = { it.step },
            label = "shareLocationStep",
        ) { step ->
            when (step) {
                is ShareLocationUiState.Permission -> {
                    PermissionStep(step, onEvent)
                }

                is ShareLocationUiState.Locating -> {
                    LocatingStep(
                        step,
                        canSchedule,
                        canOpenMaps,
                        onEvent,
                        onSend,
                        onSchedule,
                    )
                }

                ShareLocationUiState.NotFound -> {
                    NotFoundStep(onEvent)
                }

                // The sheet is gone: nothing to show while it hides.
                ShareLocationUiState.Closed -> {}
            }
        }
    }
}

/** Which step [this] shows: the steps crossfade, a better location within one does not. */
private val ShareLocationUiState.step: Int
    get() =
        when (this) {
            ShareLocationUiState.Closed -> 0
            is ShareLocationUiState.Permission -> 1
            is ShareLocationUiState.Locating -> if (location == null) 2 else 3
            ShareLocationUiState.NotFound -> 4
        }

/** Why Pingogram asks for the location, with "Not now" and "Continue" (or "Open settings" once Android won't ask). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionStep(
    state: ShareLocationUiState.Permission,
    onEvent: (ShareLocationEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        val denied = state.deniedForGood
        LocationBadge(MaterialShapes.Cookie9Sided)
        Text(
            text =
                stringResource(
                    if (denied) R.string.location_permission_denied_body else R.string.location_permission_body,
                ),
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        StepActions(
            secondary = R.string.location_not_now,
            onSecondary = { onEvent(ShareLocationEvent.Dismiss) },
        ) {
            PrimaryButton(
                text = if (denied) R.string.location_permission_settings else R.string.location_permission_continue,
                onClick = { onEvent(ShareLocationEvent.Allow) },
            )
        }
    }
}

/**
 * The search: a loading indicator until a first location, then the location with its accuracy, "Open in maps" and the
 * send split button.
 */
@Suppress("LongParameterList") // The step, what the send menu offers, and what its buttons do.
@Composable
private fun LocatingStep(
    state: ShareLocationUiState.Locating,
    canSchedule: Boolean,
    canOpenMaps: Boolean,
    onEvent: (ShareLocationEvent) -> Unit,
    onSend: (DeviceLocation, SendOptions) -> Unit,
    onSchedule: () -> Unit,
) {
    val location = state.location
    if (location == null) {
        Searching()
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        FoundLocation(state, location, onEvent)
        StepActions(
            secondary = R.string.location_open_in_maps.takeIf { canOpenMaps },
            onSecondary = { onEvent(ShareLocationEvent.OpenInMaps) },
        ) {
            SendButton(
                canSchedule = canSchedule,
                onSend = { onSend(location, SendOptions()) },
                onSendSilently = { onSend(location, SendOptions(silent = true)) },
                onSchedule = onSchedule,
            )
        }
    }
}

/** The loading indicator once the search takes a moment, and what it does, announced to screen readers. */
@Composable
private fun Searching() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Box(Modifier.size(MaterialTheme.sizes.headlineBadge), contentAlignment = Alignment.Center) {
            DelayedLoadingIndicator()
        }
        Text(
            text = stringResource(R.string.location_searching),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The location found: a badge, its coordinates (`tnum`, so digits do not dance as it improves) and accuracy, which
 * screen readers hear as one; the wavy indicator while it improves; for an approximate location, the note and
 * "Use precise location".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FoundLocation(
    state: ShareLocationUiState.Locating,
    location: DeviceLocation,
    onEvent: (ShareLocationEvent) -> Unit,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val coordinates = remember(location.location, locale) { resources.coordinatesText(location.location, locale) }
    val distance =
        remember(location.accuracyMeters, locale) { location.accuracyMeters?.let { accuracyText(it, locale) } }
    val accuracy = distance?.let { stringResource(R.string.location_accuracy, it) }
    LocationBadge(MaterialShapes.Cookie9Sided)
    Column(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription =
                    listOfNotNull(
                        resources.coordinatesText(location.location, locale, spoken = true),
                        accuracy,
                    ).joinToString(separator = ". ")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Text(
            text = coordinates,
            modifier = Modifier.clearAndSetSemantics {},
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
        )
        if (accuracy != null) {
            Text(
                text = accuracy,
                modifier = Modifier.clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
        }
    }
    RefiningNote(visible = state.isRefining)
    if (state.isApproximate) ApproximateNote(onEvent)
}

/** While a better location is still likely: the wavy indicator and "Improving accuracy…". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RefiningNote(visible: Boolean) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            LinearWavyProgressIndicator(Modifier.widthIn(max = MaterialTheme.sizes.mediaMaxWidth).fillMaxWidth())
            Text(
                text = stringResource(R.string.location_refining),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** An approximate location: says so, with "Use precise location". */
@Composable
private fun ApproximateNote(onEvent: (ShareLocationEvent) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.location_approximate),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(onClick = { onEvent(ShareLocationEvent.AskPrecise) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.location_use_precise), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** No location found: why that may be, and "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotFoundStep(onEvent: (ShareLocationEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        LocationBadge(MaterialShapes.Cookie4Sided)
        Text(
            text = stringResource(R.string.location_not_found_title),
            modifier =
                Modifier.padding(top = MaterialTheme.spacing.small).semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.location_not_found_body),
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        StepActions(
            secondary = R.string.location_not_now,
            onSecondary = { onEvent(ShareLocationEvent.Dismiss) },
            modifier = Modifier.padding(top = MaterialTheme.spacing.small),
        ) {
            PrimaryButton(text = R.string.location_try_again, onClick = { onEvent(ShareLocationEvent.Retry) })
        }
    }
}

private const val TABULAR_FIGURES = "tnum"
