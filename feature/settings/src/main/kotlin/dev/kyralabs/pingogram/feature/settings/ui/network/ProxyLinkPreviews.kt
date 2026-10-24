package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.ProxyPreviewStates as States

// Previews of the proxy link sheet's content in each of its states.

@PingogramPreviews
@Composable
private fun ProxyLinkPreview() = Link(States.link)

@PingogramPreviews
@Composable
private fun ProxyLinkCheckingPreview() = Link(States.linkChecking)

@PingogramPreviews
@Composable
private fun ProxyLinkInUsePreview() = Link(States.linkInUse)

@PingogramPreviews
@Composable
private fun ProxyLinkConnectingPreview() = Link(States.linkConnecting)

@PingogramPreviews
@Composable
private fun ProxyLinkErrorPreview() = Link(States.linkError)

@PingogramPreviews
@Composable
private fun ProxyLinkConnectedPreview() = Link(States.linkConnected)

@Composable
private fun Link(state: ProxyLinkUiState) {
    PingogramPreview { ProxyLinkContent(state, onEvent = {}, onClose = {}) }
}
