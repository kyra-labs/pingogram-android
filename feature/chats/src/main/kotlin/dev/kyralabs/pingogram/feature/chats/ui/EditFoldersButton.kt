package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "Edit folders" at the end of the folder tabs (`docs/DESIGN_SYSTEM.md` §6 "Folder tabs"; owner decision 2026-11-06):
 * an [OutlinedIconButton] (40 dp in its 48 dp touch target, `IconButtonDefaults.shapes()`) with the tune symbol and a
 * plain tooltip, which opens the chat folder settings ([onClick]) as the tab menu's "Edit folders" does. Disabled in
 * selection mode, as the tabs are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditFoldersButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.chat_list_edit_folders)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        OutlinedIconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(painter = painterResource(UiR.drawable.ic_tune), contentDescription = label)
        }
    }
}
