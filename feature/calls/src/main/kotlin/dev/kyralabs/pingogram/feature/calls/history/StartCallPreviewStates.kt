package dev.kyralabs.pingogram.feature.calls.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** The states of "Start a call", for previews and goldens. */
internal object StartCallPreviewStates {
    val loading = StartCallUiState.Loading

    val contacts =
        StartCallUiState.Content(
            people =
                persistentListOf(
                    StartCallPerson(7, "Grace Hopper", null),
                    StartCallPerson(8, "Alan Turing", null),
                    StartCallPerson(9, "Katherine Johnson", null),
                    StartCallPerson(10, "Edsger Dijkstra", null),
                ),
            canVideoCall = true,
        )

    val noCamera = contacts.copy(canVideoCall = false)

    val noMatches = StartCallUiState.Content(persistentListOf(), canVideoCall = true, noMatches = true)

    val noContacts = StartCallUiState.Content(persistentListOf(), canVideoCall = true)

    val error = StartCallUiState.Error(DomainError.Network)
}

@Composable
private fun Preview(
    state: StartCallUiState,
    query: String = "",
) {
    PingogramPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            StartCallContent(state, query, onQuery = {}, onChoose = {})
        }
    }
}

@PingogramPreviews
@Composable
private fun StartCallContactsPreview() = Preview(StartCallPreviewStates.contacts)

@PingogramPreviews
@Composable
private fun StartCallNoCameraPreview() = Preview(StartCallPreviewStates.noCamera)

@PingogramPreviews
@Composable
private fun StartCallLoadingPreview() = Preview(StartCallPreviewStates.loading)

@PingogramPreviews
@Composable
private fun StartCallNoMatchesPreview() = Preview(StartCallPreviewStates.noMatches, query = "zz")

@PingogramPreviews
@Composable
private fun StartCallNoContactsPreview() = Preview(StartCallPreviewStates.noContacts)

@PingogramPreviews
@Composable
private fun StartCallErrorPreview() = Preview(StartCallPreviewStates.error)
