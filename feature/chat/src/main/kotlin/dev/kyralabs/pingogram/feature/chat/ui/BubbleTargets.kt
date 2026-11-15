package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.feature.chat.R

/**
 * Opens the profile of user [userId] ([ChatEvent.OpenProfile]) when this sender's name or avatar is clicked, where
 * there is a user (`docs/audit/accessibility.md` A4): an [openTarget] labelled "Open profile".
 */
@Composable
internal fun Modifier.openProfileTarget(
    userId: Long?,
    shape: Shape,
    onEvent: (ChatEvent) -> Unit,
): Modifier {
    if (userId == null) return this
    val open = remember(userId, onEvent) { { onEvent(ChatEvent.OpenProfile(userId)) } }
    return openTarget(R.string.chat_open_profile, shape, open)
}

/**
 * Sends [onClick] when this part of a bubble is clicked: a button labelled [label] on a 48 dp touch target that moves
 * nothing ([touchTarget]), focusable by the keyboard, with Material's pressed, hover and focus states clipped to
 * [shape]. The label is read from the resources in the semantics block, only when something reads it.
 */
@Composable
internal fun Modifier.openTarget(
    @StringRes label: Int,
    shape: Shape,
    onClick: () -> Unit,
): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val resources = LocalResources.current
    return touchTarget(
        Modifier
            .clickable(interactions, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                onClick(label = resources.getString(label)) {
                    onClick()
                    true
                }
            },
    ).clip(shape)
        .indication(interactions, ripple(focusRingShape = shape))
}
