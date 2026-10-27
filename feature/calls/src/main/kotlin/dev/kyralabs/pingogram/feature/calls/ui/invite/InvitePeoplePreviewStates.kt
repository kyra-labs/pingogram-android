package dev.kyralabs.pingogram.feature.calls.ui.invite

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** The states of "Add people", for previews and goldens. */
internal object InvitePeoplePreviewStates {
    val loading = InvitePeopleUiState.Loading

    val contacts =
        InvitePeopleUiState.Content(
            candidates =
                persistentListOf(
                    InviteCandidate(7, "Grace Hopper", null, isPicked = true),
                    InviteCandidate(8, "Alan Turing", null, isPicked = false),
                    InviteCandidate(9, "Katherine Johnson", null, isPicked = true),
                    InviteCandidate(10, "Edsger Dijkstra", null, isPicked = false),
                ),
            pickedCount = 2,
        )

    val noMatches = InvitePeopleUiState.Content(persistentListOf(), pickedCount = 0, noMatches = true)

    val noContacts = InvitePeopleUiState.Content(persistentListOf(), pickedCount = 0)

    val error = InvitePeopleUiState.Error(DomainError.Network)
}

@Composable
private fun Preview(
    state: InvitePeopleUiState,
    query: String = "",
) {
    PingogramPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            InvitePeopleContent(state, query, {}, {}, {}, {})
        }
    }
}

@PingogramPreviews
@Composable
private fun InvitePeopleContactsPreview() = Preview(InvitePeoplePreviewStates.contacts)

@PingogramPreviews
@Composable
private fun InvitePeopleLoadingPreview() = Preview(InvitePeoplePreviewStates.loading)

@PingogramPreviews
@Composable
private fun InvitePeopleNoMatchesPreview() = Preview(InvitePeoplePreviewStates.noMatches, query = "zz")

@PingogramPreviews
@Composable
private fun InvitePeopleNoContactsPreview() = Preview(InvitePeoplePreviewStates.noContacts)

@PingogramPreviews
@Composable
private fun InvitePeopleErrorPreview() = Preview(InvitePeoplePreviewStates.error)
