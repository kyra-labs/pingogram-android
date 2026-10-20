package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.sizes

/**
 * How every reaction button of the sheet looks: medium [ToggleButton]s in Material's shapes for that height, which
 * morph between round and square as they are checked, `surfaceContainerHighest` unchosen and `secondaryContainer`
 * chosen like the badges on bubbles. Made once per sheet, not per button.
 */
@Immutable
internal class ReactionButtonStyle(
    val height: Dp,
    val shapes: ToggleButtonShapes,
    val colors: ToggleButtonColors,
)

@Composable
internal fun rememberReactionButtonStyle(): ReactionButtonStyle {
    val scheme = MaterialTheme.colorScheme
    val height = ButtonDefaults.MediumContainerHeight
    val shapes = ToggleButtonDefaults.shapesFor(height)
    val colors =
        ToggleButtonDefaults.colors(
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
            checkedContainerColor = scheme.secondaryContainer,
            checkedContentColor = scheme.onSecondaryContainer,
        )
    return remember(height, shapes, colors) { ReactionButtonStyle(height, shapes, colors) }
}

/**
 * A reaction of the sheet, in its quick row or its picker: a [ToggleButton] in [style], checked if the user chose it,
 * with its emoji in `titleLarge` (a custom emoji's [picture] or its placeholder). A tapped one changes at once, with a
 * toggle haptic, while the sheet hides ([onToggle]).
 */
@Composable
internal fun ReactionToggleButton(
    reaction: ReactionOption,
    picture: Int?,
    style: ReactionButtonStyle,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val resources = LocalResources.current
    var checked by remember(reaction) { mutableStateOf(reaction.isChosen) }
    ToggleButton(
        checked = checked,
        onCheckedChange = { now ->
            checked = now
            haptics.performHapticFeedback(if (now) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onToggle()
        },
        modifier =
            modifier.heightIn(min = style.height).semantics {
                contentDescription = reactionToggleLabel(resources, reaction.label, reaction.isChosen)
            },
        shapes = style.shapes,
        colors = style.colors,
        contentPadding = ButtonDefaults.SmallContentPadding,
    ) {
        val colors = style.colors
        ReactionGlyph(
            label = reaction.label,
            style = MaterialTheme.typography.titleLarge,
            customEmojiSize = MaterialTheme.sizes.inlineIcon,
            placeholderColor = { if (checked) colors.checkedContentColor else colors.contentColor },
            customEmojiPicture = picture,
        )
    }
}
