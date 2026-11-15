package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A deleted account's "Delete chat" (board 24g), or a bot's "Share" of its link, in Call's place as bots are not
 * called (board 24d); nothing for anyone else.
 */
@Composable
internal fun RowScope.DeleteOrSharePill(
    actions: ProfileActions,
    onEvent: (ProfileEvent) -> Unit,
) {
    if (actions.canDeleteChat) {
        ActionPill(
            icon = UiR.drawable.ic_delete,
            label = stringResource(R.string.profile_action_delete_chat),
            onClick = { onEvent(ProfileEvent.DeleteChat) },
            modifier = Modifier.weight(1f),
        )
    }
    actions.shareUsername?.let { username ->
        ActionPill(
            icon = UiR.drawable.ic_share,
            label = stringResource(R.string.profile_action_share),
            onClick = { onEvent(ProfileEvent.ShareUsername(username)) },
            modifier = Modifier.weight(1f),
        )
    }
}
