package dev.kyralabs.pingogram.feature.chat.ui.panel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import kotlinx.collections.immutable.ImmutableList

/**
 * The stickers suggested for the one emoji the composer holds alone (Settings → Stickers → "Suggest stickers by
 * emoji"), above the composer as the "/" suggestions show: a row of the panel's sticker cells ([PanelSticker],
 * `sizes.panelSticker`) on `surfaceContainer` (the pill's color) with `large` corners, inset `spacing.small` as the
 * pill is, scrolling sideways, growing in and shrinking away on the motion scheme's springs. A tap sends the sticker
 * ([onPick]), which takes the emoji's place. Screen readers hear a pane, "Sticker suggestions". Nothing while
 * [enabled] is false (editing, recording).
 */
@Composable
internal fun StickerSuggestionsArea(
    stickers: ImmutableList<Sticker>,
    onPick: (Sticker) -> Unit,
    enabled: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = enabled && stickers.isNotEmpty(),
        enter =
            expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Bottom) +
                fadeIn(motion.defaultEffectsSpec()),
        exit =
            shrinkVertically(motion.fastSpatialSpec(), shrinkTowards = Alignment.Bottom) +
                fadeOut(motion.fastEffectsSpec()),
    ) {
        val title = stringResource(R.string.panel_sticker_suggestions)
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.small)
                    .padding(top = MaterialTheme.spacing.small)
                    .semantics { paneTitle = title },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            LazyRow(
                contentPadding = PaddingValues(MaterialTheme.spacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
                items(stickers, key = { it.file.id }, contentType = { SUGGESTED_STICKER }) { sticker ->
                    PanelSticker(sticker, onPick, Modifier.size(MaterialTheme.sizes.panelSticker))
                }
            }
        }
    }
}

/**
 * Hands the field's text to the panel's view model ([EmojiPanelEvent.TextChanged]), which suggests stickers for one
 * emoji alone, and shows them ([StickerSuggestionsArea]): a tapped sticker is sent ([onSendSticker]) and the emoji
 * cleared. Not while editing (a sticker cannot replace an edited text) or recording: [enabled] false.
 */
@Composable
internal fun StickerSuggestionsInput(
    text: ComposerText,
    panel: EmojiPanelUiState,
    onPanelEvent: (EmojiPanelEvent) -> Unit,
    onSendSticker: (Sticker) -> Unit,
    enabled: Boolean,
) {
    val currentOnPanelEvent by rememberUpdatedState(onPanelEvent)
    LaunchedEffect(text) {
        snapshotFlow { text.state.text.toString() }.collect { currentOnPanelEvent(EmojiPanelEvent.TextChanged(it)) }
    }
    StickerSuggestionsArea(
        stickers = panel.suggestions,
        onPick = { sticker ->
            onSendSticker(sticker)
            text.state.clearText()
        },
        enabled = enabled,
    )
}

private const val SUGGESTED_STICKER = "suggested-sticker"
