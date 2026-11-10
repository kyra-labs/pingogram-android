package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.media.ui.preview.PreviewImages
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The picking screen's states for previews and goldens: four items, the first adding, one, and one still adding. */
internal object NewStoryPreviewData {
    private fun photo(number: Int) = StoryPick("photo$number.jpg", "photo$number.jpg", isVideo = false)

    private val video = StoryPick("video3.mp4", "video3.jpg", isVideo = true, durationSeconds = 14)

    /** Four items, the first shown: the board's state. */
    val picked = NewStoryUiState(persistentListOf(photo(1), photo(2), video, photo(4)), selected = "photo1.jpg")

    /** The video shown, two more still being added. */
    val videoShown = picked.copy(selected = "video3.mp4", preparing = 2)

    /** One photo. */
    val single = NewStoryUiState(persistentListOf(photo(1)))

    /** The first item still being added. */
    val adding = NewStoryUiState(preparing = 1)

    /** Ten items: the post is full, so "Add more" and "Camera" give way. */
    val full = NewStoryUiState((1..MAX_STORY_ITEMS).map(::photo).toImmutableList())
}

internal class NewStoryPreviewStates : PreviewParameterProvider<NewStoryUiState> {
    override val values: Sequence<NewStoryUiState> =
        with(NewStoryPreviewData) { sequenceOf(picked, videoShown, single, adding, full) }
}

@PingogramPreviews
@Composable
private fun NewStoryScreenPreview(
    @PreviewParameter(NewStoryPreviewStates::class) state: NewStoryUiState,
) {
    PingogramPreview {
        PreviewImages { NewStoryScreen(state = state, onEvent = {}) }
    }
}
