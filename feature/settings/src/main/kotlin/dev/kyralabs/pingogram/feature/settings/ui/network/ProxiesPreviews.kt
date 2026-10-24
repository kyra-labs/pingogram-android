package dev.kyralabs.pingogram.feature.settings.ui.network

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.settings.ui.preview.ProxyPreviewStates as States

// The previews of the Proxy page, one per state.

@PingogramPreviews
@Composable
private fun ProxiesPreview() = Proxies(States.proxies)

@PingogramPreviews
@Composable
private fun ProxiesOffPreview() = Proxies(States.proxiesOff)

@PingogramPreviews
@Composable
private fun ProxiesEmptyPreview() = Proxies(States.proxiesEmpty)

@PingogramPreviews
@Composable
private fun ProxiesDeletingPreview() = Proxies(States.proxiesDeleting)

@PingogramPreviews
@Composable
private fun ProxiesLoadingPreview() = Proxies(States.proxiesLoading)

@PingogramPreviews
@Composable
private fun ProxiesErrorPreview() = Proxies(States.proxiesError)

@Composable
private fun Proxies(state: ProxiesUiState) {
    PingogramPreview { ProxiesScreen(state, onEvent = {}, onAddProxy = {}, onEditProxy = {}, onBack = {}) }
}
