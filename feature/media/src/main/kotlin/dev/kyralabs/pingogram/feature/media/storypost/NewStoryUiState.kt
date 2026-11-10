package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The most photos and videos a story post takes (`docs/adr/0036-posting-stories.md`). */
internal const val MAX_STORY_ITEMS = 10

/**
 * The photos and videos picked for a new story (board 10a, `docs/DESIGN_SYSTEM.md` §6 "New story"), in the order they
 * will be posted; [selected] is the key of the one the large preview shows, and [preparing] counts those still being
 * copied, shown after them.
 */
@Immutable
internal data class NewStoryUiState(
    val items: ImmutableList<StoryPick> = persistentListOf(),
    val selected: String? = null,
    val preparing: Int = 0,
    val step: NewStoryStep = NewStoryStep.Pick,
) {
    /** How many more items the post takes. */
    val remaining: Int get() = (MAX_STORY_ITEMS - items.size - preparing).coerceAtLeast(0)

    val canGoNext: Boolean get() = items.isNotEmpty() && preparing == 0

    /** The item the large preview shows: the selected one, else the first. */
    val shown: StoryPick? get() = items.firstOrNull { it.key == selected } ?: items.firstOrNull()
}

/** The screen's two steps: picking the items (board 10a), then composing the story (board 10b). */
internal enum class NewStoryStep { Pick, Compose }

/**
 * One picked item: a photo, or a video of [durationSeconds]. [image] is what shows for it, the photo itself or the
 * video's still (null without one); [key] is its copy's path, unique while it is picked.
 */
@Immutable
internal data class StoryPick(
    val key: String,
    val image: String?,
    val isVideo: Boolean,
    val durationSeconds: Int = 0,
)

/** What the user does on the picking screen. */
internal sealed interface NewStoryEvent {
    /** The photo picker returned [uris], in the order picked; none when it was cancelled. */
    data class Picked(
        val uris: List<String>,
    ) : NewStoryEvent

    /** "Add more": the photo picker again, for the places left. */
    data object AddMore : NewStoryEvent

    data object TakePhoto : NewStoryEvent

    /** The camera app returned, with a photo if [taken]. */
    data class PhotoTaken(
        val taken: Boolean,
    ) : NewStoryEvent

    data class Select(
        val key: String,
    ) : NewStoryEvent

    data class Remove(
        val key: String,
    ) : NewStoryEvent

    /** Moves item [key] to position [to]. */
    data class Move(
        val key: String,
        val to: Int,
    ) : NewStoryEvent

    /** On to composing the story. */
    data object Next : NewStoryEvent

    /** Back from composing to picking. */
    data object Back : NewStoryEvent

    data class SetAudience(
        val audience: StoryAudience,
    ) : NewStoryEvent

    data object Post : NewStoryEvent

    data object Close : NewStoryEvent
}

/** What the screen does once. */
internal sealed interface NewStoryEffect {
    /** Opens Android's photo picker for at most [max] photos and videos. */
    data class OpenPicker(
        val max: Int,
    ) : NewStoryEffect

    /** Opens the camera app to take a photo into the file at [path]. */
    data class LaunchCamera(
        val path: String,
    ) : NewStoryEffect

    data class Failed(
        val error: DomainError,
    ) : NewStoryEffect

    /** A picked file is no photo or video Android can read, and cannot go into a story. */
    data object NotPostable : NewStoryEffect

    /** TDLib took the stories, which it now uploads: the screen closes. */
    data object Posted : NewStoryEffect

    data object Close : NewStoryEffect
}
