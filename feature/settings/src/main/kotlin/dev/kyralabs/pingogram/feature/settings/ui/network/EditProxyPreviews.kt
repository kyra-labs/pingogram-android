package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.ProxyPreviewStates as States

// The previews of the page that adds or edits a proxy, one per state.

@PingogramPreviews
@Composable
private fun NewProxyPreview() = EditProxy(States.newProxy)

@PingogramPreviews
@Composable
private fun EditProxyPreview() = EditProxy(States.editProxy)

@PingogramPreviews
@Composable
private fun HttpProxyPreview() = EditProxy(States.httpProxy)

@PingogramPreviews
@Composable
private fun MtprotoErrorsPreview() = EditProxy(States.mtprotoErrors)

@PingogramPreviews
@Composable
private fun SavingProxyPreview() = EditProxy(States.savingProxy)

@Composable
private fun EditProxy(state: EditProxyUiState) {
    PingogramPreview { EditProxyScreen(state, onEvent = {}, onBack = {}) }
}
