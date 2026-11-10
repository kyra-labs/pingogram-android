package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.StoryPostAbility
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
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

/** The composer's states for previews and goldens: to close friends, blocked by the week's limit, posting. */
internal object StoryComposePreviewData {
    val items = NewStoryPreviewData.picked.copy(step = NewStoryStep.Compose)

    val closeFriends =
        StoryComposeState(
            audience = StoryAudience.CloseFriends,
            ability = StoryPostAbility.Allowed(3),
            closeFriends = 14,
        )

    val weeklyLimit = StoryComposeState(ability = StoryPostAbility.WeeklyLimit(WEEKLY_LIMIT_SECONDS))

    val posting = closeFriends.copy(audience = StoryAudience.Everyone, isPosting = true)

    /** "Kapu beach — we got lucky with the clouds tonight", its first words bold. */
    fun caption() =
        CaptionText(
            FormattedText(
                "Kapu beach — we got lucky with the clouds tonight",
                persistentListOf(TextEntity(0, CAPTION_BOLD, TextEntityType.Bold)),
            ),
        )

    private const val WEEKLY_LIMIT_SECONDS = 2 * 3_600
    private const val CAPTION_BOLD = 10
}

internal class StoryComposePreviewStates : PreviewParameterProvider<StoryComposeState> {
    override val values: Sequence<StoryComposeState> =
        with(StoryComposePreviewData) { sequenceOf(closeFriends, weeklyLimit, posting) }
}

@PingogramPreviews
@Composable
private fun StoryComposeScreenPreview(
    @PreviewParameter(StoryComposePreviewStates::class) compose: StoryComposeState,
) {
    PingogramPreview {
        PreviewImages {
            StoryComposeScreen(
                state = StoryComposePreviewData.items,
                compose = compose,
                caption = StoryComposePreviewData.caption(),
                onEvent = {},
            )
        }
    }
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
