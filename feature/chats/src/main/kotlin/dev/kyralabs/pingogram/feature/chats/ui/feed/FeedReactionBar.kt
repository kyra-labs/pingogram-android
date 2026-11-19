package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * The reactions bar above a lifted feed card (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 6): the post's
 * [emojis] on a `surfaceContainerHigh` pill with the menu's shadow, each a 48 dp target, the user's own ([chosen]) on
 * `primaryContainer`. A tap adds the reaction, or takes it away if it is the user's ([onReact]). It opens above the
 * card, below it where the top of the window leaves no room.
 */
@Composable
internal fun FeedReactionBar(
    emojis: ImmutableList<String>,
    chosen: ImmutableSet<String>,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        popupPositionProvider = AboveAnchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = MenuDefaults.ShadowElevation,
        ) {
            Row(Modifier.padding(horizontal = MaterialTheme.spacing.extraSmall)) {
                emojis.forEach { emoji ->
                    val isChosen = emoji in chosen
                    Box(
                        modifier =
                            Modifier
                                .selectable(selected = isChosen, role = Role.Button) { onReact(emoji) }
                                .minimumInteractiveComponentSize()
                                .then(
                                    if (isChosen) {
                                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

/** Places a popup above its anchor, centred on it and within the window; below it where there is no room above. */
private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x =
            (anchorBounds.center.x - popupContentSize.width / 2)
                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height
        return IntOffset(x, if (above >= 0) above else anchorBounds.bottom)
    }
}
