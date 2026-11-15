package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonDefaults.IconButtonWidthOption
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * More, the call panel's fourth button (board 27, owner decision 2026-11-15: Hold moves into More; the panel is Mute,
 * Route, Camera, More, End): the panel's wide tonal button over its label, which opens an expressive
 * [DropdownMenuPopup] of "Hold" (or "Resume" while held, disabled until the call is connected) and, where both sides
 * can make it a group call, "Add people". Screen readers hear "On hold" as the button's state while the call is held.
 */
@Composable
internal fun CallMoreButton(
    isOnHold: Boolean,
    canHold: Boolean,
    canAddPeople: Boolean,
    onEvent: (CallEvent) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val held = stringResource(R.string.call_status_on_hold)
    LabeledButton(R.string.call_more) {
        Box {
            FilledTonalIconButton(
                onClick = { expanded = true },
                shapes = IconButtonDefaults.shapes(),
                colors =
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                modifier =
                    Modifier
                        .size(IconButtonDefaults.mediumContainerSize(IconButtonWidthOption.Wide))
                        .semantics { if (isOnHold) stateDescription = held },
            ) {
                Icon(
                    painterResource(UiR.drawable.ic_more_vert),
                    contentDescription = stringResource(R.string.call_more),
                    modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                )
            }
            DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
                MoreItems(isOnHold, canHold, canAddPeople) { event ->
                    expanded = false
                    onEvent(event)
                }
            }
        }
    }
}

/** More's items: "Hold" or "Resume", and "Add people" where [canAddPeople]; a choice goes to [choose]. */
@Composable
private fun MoreItems(
    isOnHold: Boolean,
    canHold: Boolean,
    canAddPeople: Boolean,
    choose: (CallEvent) -> Unit,
) {
    val count = if (canAddPeople) 2 else 1
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
        DropdownMenuItem(
            onClick = { choose(CallEvent.ToggleHold) },
            text = { Text(stringResource(if (isOnHold) R.string.call_resume else R.string.call_hold)) },
            shape = MenuDefaults.itemShape(0, count).shape,
            leadingIcon = {
                Icon(
                    painterResource(if (isOnHold) UiR.drawable.ic_play_arrow_filled else UiR.drawable.ic_pause_filled),
                    contentDescription = null,
                )
            },
            enabled = canHold,
        )
        if (canAddPeople) {
            DropdownMenuItem(
                onClick = { choose(CallEvent.OpenAddPeople) },
                text = { Text(stringResource(R.string.group_call_add_people)) },
                shape = MenuDefaults.itemShape(1, count).shape,
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_person_add), contentDescription = null) },
            )
        }
    }
}
