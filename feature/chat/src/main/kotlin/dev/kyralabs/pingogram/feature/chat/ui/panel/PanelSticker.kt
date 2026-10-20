package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.StickerImage
import dev.kyralabs.pingogram.feature.chat.ui.content.fitInSquare
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A sticker in the panel's grid, which tapping sends ([onClick]) with a confirming haptic: the sticker as in a message
 * ([StickerImage], animated for TGS), fitted into the smallest cell less its padding, centred in the cell, with a
 * `large` rounded ripple. Screen readers hear "😀 Sticker" and "Send".
 */
@Composable
internal fun PanelSticker(
    sticker: Sticker,
    onClick: (Sticker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description =
        if (sticker.emoji.isBlank()) {
            stringResource(UiR.string.preview_sticker)
        } else {
            stringResource(UiR.string.preview_sticker_emoji, sticker.emoji)
        }
    val haptics = LocalHapticFeedback.current
    val padding = MaterialTheme.spacing.extraSmall
    val side = MaterialTheme.sizes.panelSticker - padding * 2
    val displaySize = remember(sticker.width, sticker.height, side) { fitInSquare(sticker.width, sticker.height, side) }
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClickLabel = stringResource(R.string.panel_sticker_send), role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick(sticker)
                }.semantics(mergeDescendants = true) { contentDescription = description }
                .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        StickerImage(sticker, displaySize)
    }
}

/** A sticker of a set not loaded yet: a tonal `large`-rounded square in `surfaceContainerHigh`, decorative. */
@Composable
internal fun StickerPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .padding(MaterialTheme.spacing.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large),
    )
}

/**
 * A sticker set's picture on its button in the row of sections: its first sticker, still, at `panelStickerCover`;
 * without one, a tonal placeholder.
 */
@Composable
internal fun StickerSetCover(cover: Sticker?) {
    val side = MaterialTheme.sizes.panelStickerCover
    if (cover == null) {
        Box(Modifier.size(side).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small))
    } else {
        val displaySize = remember(cover.width, cover.height, side) { fitInSquare(cover.width, cover.height, side) }
        StickerImage(cover, DpSize(displaySize.width, displaySize.height), animate = false)
    }
}
