package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * A compact row's one line (Settings → Chats → "Compact chat list", `REQUIREMENTS.md` FR-X19,
 * `docs/DESIGN_SYSTEM.md` §6 "Chat list row"): the title as a full row shows it, then the [preview] in what is left,
 * each cut with an ellipsis, the title taking at most half while the preview needs the rest. One line keeps the row at
 * the list item's one-line height; a forum's topics are left out, its last message shows as any chat's.
 */
@Composable
internal fun CompactRowHeadline(
    item: ChatListItemModel,
    title: String,
    preview: PreviewText,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f, fill = false)) { ChatRowTitle(item, title) }
        Box(Modifier.weight(1f)) {
            ChatPreviewText(preview, (item.preview as? ChatPreview.Message)?.contentIcon)
        }
    }
}
