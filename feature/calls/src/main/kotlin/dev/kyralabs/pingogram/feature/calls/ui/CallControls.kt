package dev.kyralabs.pingogram.feature.calls.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonDefaults.IconButtonWidthOption
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.AudioRouteType
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The controls the call screen and the voice chat's screen share (docs/DESIGN_SYSTEM.md §6, "Call screen", "Voice chat
// screen"): "Back to the app", the panel's toggles and audio route, their labels, and the red hang-up pill.

/** "Back to the app": the screen's top start; over video in the media overlay's colors. */
@Composable
internal fun MinimizeButton(
    onMinimize: () -> Unit,
    overlay: Boolean = false,
) {
    IconButton(
        onClick = onMinimize,
        colors =
            if (overlay) {
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.pingogramColors.mediaOverlay,
                    contentColor = MaterialTheme.pingogramColors.onMediaOverlay,
                )
            } else {
                IconButtonDefaults.iconButtonColors()
            },
    ) {
        Icon(
            painterResource(UiR.drawable.ic_keyboard_arrow_down),
            contentDescription = stringResource(R.string.call_minimize),
        )
    }
}

/** Hang up: a wide medium button in `error`, with the press morph. */
@Composable
internal fun HangUpButton(
    @StringRes label: Int = R.string.call_hang_up,
    onClick: () -> Unit,
) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(height),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier = Modifier.fillMaxWidth(HANG_UP_WIDTH_FRACTION).heightIn(min = height),
    ) {
        Icon(
            painterResource(R.drawable.ic_call_end_filled),
            contentDescription = stringResource(label),
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
    }
}

/**
 * The audio route: with only the earpiece and the speaker, a toggle between them; with a headset or Bluetooth too, a
 * menu of every route, the current one selected.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RouteButton(
    route: AudioRoute?,
    routes: List<AudioRoute>,
    onSelect: (AudioRoute) -> Unit,
) {
    val speaker = routes.firstOrNull { it.type == AudioRouteType.Speaker }
    val other = routes.firstOrNull { it.type == AudioRouteType.Earpiece }
    val simple = routes.all { it.type == AudioRouteType.Speaker || it.type == AudioRouteType.Earpiece }
    var menuOpen by remember { mutableStateOf(false) }
    LabeledButton(if (simple) R.string.call_speaker else R.string.call_audio) {
        Box {
            Toggle(
                checked =
                    route?.type == AudioRouteType.Speaker ||
                        (!simple && route != null && route.type != AudioRouteType.Earpiece),
                icon = if (simple) R.drawable.ic_volume_up_filled else route?.type.icon(),
                label = if (simple) R.string.call_speaker else R.string.call_audio,
                enabled = routes.size > 1,
            ) {
                if (simple) {
                    val target = if (route?.type == AudioRouteType.Speaker) other else speaker
                    target?.let(onSelect)
                } else {
                    menuOpen = true
                }
            }
            DropdownMenuPopup(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                    routes.forEachIndexed { index, item ->
                        val selected = item == route
                        DropdownMenuItem(
                            text = { Text(item.label()) },
                            onClick = {
                                menuOpen = false
                                onSelect(item)
                            },
                            shape = MenuDefaults.itemShape(index, routes.size).shape,
                            // The route in use shows a check in its icon's place.
                            leadingIcon = {
                                if (selected) {
                                    SelectedCheck()
                                } else {
                                    Icon(
                                        painterResource(item.type.icon()),
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The check after the route in use. */
private val SelectedCheck: @Composable () -> Unit = {
    Icon(painterResource(UiR.drawable.ic_check), contentDescription = stringResource(R.string.call_route_selected))
}

@Composable
private fun AudioRoute.label(): String =
    when (type) {
        AudioRouteType.Earpiece -> stringResource(R.string.call_route_phone)
        AudioRouteType.Speaker -> stringResource(R.string.call_route_speaker)
        AudioRouteType.WiredHeadset -> stringResource(R.string.call_route_headset)
        AudioRouteType.Bluetooth -> name.ifBlank { stringResource(R.string.call_route_bluetooth) }
    }

@DrawableRes
private fun AudioRouteType?.icon(): Int =
    when (this) {
        AudioRouteType.Speaker -> R.drawable.ic_volume_up_filled
        AudioRouteType.WiredHeadset -> R.drawable.ic_headphones_filled
        AudioRouteType.Bluetooth -> R.drawable.ic_bluetooth
        AudioRouteType.Earpiece, null -> R.drawable.ic_phone_in_talk_filled
    }

/**
 * A medium wide toggle button on the panel, `surfaceBright` (`primary` when checked), its shape morphing from round to
 * square when checked, with a toggle haptic.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Toggle(
    checked: Boolean,
    @DrawableRes icon: Int,
    @StringRes label: Int,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onToggle()
        },
        enabled = enabled,
        shapes = IconButtonDefaults.toggleableShapes(),
        colors =
            IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceBright,
                contentColor = MaterialTheme.colorScheme.onSurface,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize(IconButtonWidthOption.Wide)),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(label),
            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
        )
    }
}

/** A button over its label; the label is for sight only, as the button carries it for screen readers. */
@Composable
internal fun LabeledButton(
    @StringRes label: Int,
    button: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        button()
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The safe area below and beside docked controls, which reach the window's bottom edge; none otherwise. */
@Composable
internal fun bottomInsets(docked: Boolean): WindowInsets =
    if (docked) WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal) else NoInsets

private val NoInsets = WindowInsets(0, 0, 0, 0)

/** The hang-up pill's width against the panel's. */
private const val HANG_UP_WIDTH_FRACTION = 0.4f
