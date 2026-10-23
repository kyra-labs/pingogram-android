package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R

/**
 * "Join channel", "Join group" or "Request to join" (`REQUIREMENTS.md` FR-P5): a default filled `Button` with the press
 * morph, centred in the composer's bar above the keyboard and system bars; disabled while Telegram is asked.
 */
@Composable
internal fun JoinButton(
    join: ComposerUiState.Join,
    onJoin: () -> Unit,
) {
    val label =
        when {
            join.requestsToJoin -> R.string.composer_request_to_join
            join.isChannel -> R.string.composer_join_channel
            else -> R.string.composer_join_group
        }
    Box(
        modifier =
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(BOTTOM_EDGES))
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Button(onClick = onJoin, enabled = !join.isJoining, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

@PingogramPreviews
@Composable
private fun ComposerJoinPreview() {
    PingogramPreview {
        ChatBottomBar(ComposerUiState.Join(isChannel = true, requestsToJoin = false), ComposerText(), {}, {})
    }
}
