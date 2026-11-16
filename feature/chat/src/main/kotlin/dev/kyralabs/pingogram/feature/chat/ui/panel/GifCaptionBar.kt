package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R

/**
 * Where a GIF sent from the panel puts the composer's text, its caption: below the GIF ([above] false, the default)
 * or above it. Kept across rotation and process death while the conversation shows.
 */
@Stable
internal class GifCaptionChoice(
    above: Boolean = false,
) {
    var above by mutableStateOf(above)

    companion object {
        val Saver: Saver<GifCaptionChoice, Boolean> = Saver(save = { it.above }, restore = { GifCaptionChoice(it) })
    }
}

@Composable
internal fun rememberGifCaptionChoice(): GifCaptionChoice =
    rememberSaveable(saver = GifCaptionChoice.Saver) { GifCaptionChoice() }

/**
 * The GIF tab's caption: what [hasCaption] says, whether the composer holds text, which a GIF tapped below takes as its
 * caption, and where it goes ([choice]).
 */
@Stable
internal class GifCaption(
    val choice: GifCaptionChoice,
    private val hasCaption: () -> Boolean,
) {
    /**
     * The composer holds text: a GIF goes with it as its caption.
     * Read in composition, so the bar follows the field.
     */
    val shows: Boolean get() = hasCaption()
}

/**
 * Above the GIFs while the composer holds text (`docs/DESIGN_SYSTEM.md` §6 "Emoji and sticker panel"), one row inset
 * `spacing.small` as the search field is: that the text goes with the GIF as its caption, in `bodySmall`
 * `onSurfaceVariant` (two lines at most), then "Below" and "Above" as connected extra-small `ToggleButton`s in their
 * 48 dp touch targets (one radio group for screen readers, a `SegmentTick` per pick).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GifCaptionBar(
    choice: GifCaptionChoice,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier.fillMaxWidth().padding(horizontal = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.chat_gif_caption_note),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            CaptionPlaceButton(choice, above = false)
            CaptionPlaceButton(choice, above = true)
        }
    }
}

/** "Below" (the leading button) or "Above" (the trailing one): where the caption goes, picked with a `SegmentTick`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CaptionPlaceButton(
    choice: GifCaptionChoice,
    above: Boolean,
) {
    val haptics = LocalHapticFeedback.current
    val height = ButtonDefaults.ExtraSmallContainerHeight
    val checked = choice.above == above
    val description =
        stringResource(
            if (above) R.string.chat_gif_caption_above_description else R.string.chat_gif_caption_below_description,
        )
    ToggleButton(
        checked = checked,
        onCheckedChange = {
            if (!checked) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                choice.above = above
            }
        },
        modifier =
            Modifier.heightIn(min = height).semantics {
                role = Role.RadioButton
                contentDescription = description
            },
        shapes =
            if (above) {
                ButtonGroupDefaults.connectedTrailingButtonShapes()
            } else {
                ButtonGroupDefaults.connectedLeadingButtonShapes()
            },
        colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        Text(
            text = stringResource(if (above) R.string.chat_gif_caption_above else R.string.chat_gif_caption_below),
            maxLines = 1,
            style = ButtonDefaults.textStyleFor(height),
            fontWeight = if (checked) MaterialTheme.typography.labelLargeEmphasized.fontWeight else null,
        )
    }
}
