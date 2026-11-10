package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** The states of "Selected people", for previews and goldens. */
internal object StoryPeoplePreviewData {
    val loading = StoryPeopleUiState.Loading

    val contacts =
        StoryPeopleUiState.Content(
            candidates =
                persistentListOf(
                    StoryPerson(7, "Grace Hopper", null, isPicked = true),
                    StoryPerson(8, "Alan Turing", null, isPicked = false),
                    StoryPerson(9, "Katherine Johnson", null, isPicked = true),
                    StoryPerson(10, "Edsger Dijkstra", null, isPicked = false),
                ),
            pickedCount = 2,
        )

    val noMatches = StoryPeopleUiState.Content(persistentListOf(), pickedCount = 0, noMatches = true)

    val noContacts = StoryPeopleUiState.Content(persistentListOf(), pickedCount = 0)

    val error = StoryPeopleUiState.Error(DomainError.Network)
}

@Composable
private fun Preview(
    state: StoryPeopleUiState,
    query: String = "",
) {
    PingogramPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            StoryPeopleContent(state, query) {}
        }
    }
}

@PingogramPreviews
@Composable
private fun StoryPeopleContactsPreview() = Preview(StoryPeoplePreviewData.contacts)

@PingogramPreviews
@Composable
private fun StoryPeopleLoadingPreview() = Preview(StoryPeoplePreviewData.loading)

@PingogramPreviews
@Composable
private fun StoryPeopleNoMatchesPreview() = Preview(StoryPeoplePreviewData.noMatches, query = "zz")

@PingogramPreviews
@Composable
private fun StoryPeopleNoContactsPreview() = Preview(StoryPeoplePreviewData.noContacts)

@PingogramPreviews
@Composable
private fun StoryPeopleErrorPreview() = Preview(StoryPeoplePreviewData.error)
