package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.screenContentWindowInsets
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The Chats tab until the chat list lands (`docs/ROADMAP.md` M2): an empty state. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatsScreen(modifier: Modifier = Modifier) {
    // The floating navigation bar covers the bottom of top-level screens.
    Scaffold(modifier, contentWindowInsets = screenContentWindowInsets()) { innerPadding ->
        EmptyState(
            illustration = MaterialShapes.Cookie9Sided,
            icon = UiR.drawable.ic_chat_filled,
            title = stringResource(R.string.chats_placeholder_title),
            body = stringResource(R.string.chats_placeholder_body),
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@PingogramPreviews
@Composable
private fun ChatsScreenPreview() {
    PingogramPreview { ChatsScreen() }
}
