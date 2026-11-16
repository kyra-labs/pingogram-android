package dev.kyralabs.pingogram.feature.chats.ui.focus

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.FocusBadge
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The profile on now under the Chats header's status line (`docs/DESIGN_SYSTEM.md` §13.13, board 31): an assist chip
 * in a full pill on `primaryContainer`, the profile's symbol on its shape, "Focus: Work hours · until 6:00 PM" in
 * `labelLargeEmphasized` and a down arrow; a tap opens the switcher ([onOpen]). Screen readers hear one button,
 * "Focus: Work hours, until 6:00 PM. Switch Focus".
 */
@Composable
internal fun FocusPillChip(
    pill: FocusPill,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val time = rememberFocusTime()
    val name = pill.name.bidiIsolated()
    val label =
        pill.endsAt?.let { stringResource(R.string.chat_list_focus_pill_until, name, time(it)) }
            ?: stringResource(R.string.chat_list_focus_pill, name)
    val spoken = stringResource(R.string.chat_list_focus_pill_spoken, label)
    val switchLabel = stringResource(R.string.chat_list_focus_switch)
    AssistChip(
        onClick = onOpen,
        label = {
            Text(
                text = label,
                // Two lines at large font scales (board 31): the header makes room for the pill as it measures.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        },
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = switchLabel) {
                    onOpen()
                    true
                }
            },
        leadingIcon = {
            FocusBadge(
                icon = pill.icon,
                size = AssistChipDefaults.IconSize,
                container = colors.onPrimaryContainer,
                content = colors.primaryContainer,
                iconSize = AssistChipDefaults.IconSize * ICON_SHARE,
            )
        },
        trailingIcon = {
            Icon(
                painter = painterResource(UiR.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
            )
        },
        shape = CircleShape,
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = colors.primaryContainer,
                labelColor = colors.onPrimaryContainer,
                trailingIconContentColor = colors.onPrimaryContainer,
            ),
        border = null,
    )
}

/** How much of the badge its symbol takes. */
private const val ICON_SHARE = 0.7f
