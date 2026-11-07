package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R

/**
 * "Pinned" above the pinned chats' group (`docs/DESIGN_SYSTEM.md` §6 "Chat list row"; owner decision 2026-11-06), in
 * `titleSmallEmphasized` and `primary`, as settings pages head their groups, lined up with the rows' content; a heading
 * for screen readers.
 */
@Composable
internal fun PinnedHeader(modifier: Modifier = Modifier) {
    val direction = LocalLayoutDirection.current
    val content = ListItemDefaults.ContentPadding
    Text(
        text = stringResource(R.string.chat_list_pinned),
        modifier =
            modifier
                .padding(
                    PaddingValues(
                        start = content.calculateStartPadding(direction),
                        end = content.calculateEndPadding(direction),
                        top = MaterialTheme.spacing.small,
                        bottom = MaterialTheme.spacing.extraSmall,
                    ),
                ).semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}
