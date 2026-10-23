package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.persistentListOf

/** The stories viewer's states for previews and goldens: a photo with a caption, a video, a reply, and what fails. */
internal object StoryViewerPreviewData {
    val posters =
        persistentListOf(
            StoryPosterUi(chatId = 2, name = "Ada Lovelace", photo = null, storyCount = 4, shownIndex = 1),
            StoryPosterUi(chatId = 3, name = "Pingogram News", photo = null, storyCount = 2, shownIndex = 0),
        )

    private val caption =
        FormattedText(
            "Sunrise over the lake, before anyone else was up. #morning",
            persistentListOf(TextEntity(offset = 50, length = 8, type = TextEntityType.Hashtag)),
        )

    private val reactions =
        StoryReactionsUi(
            available =
                persistentListOf(
                    ReactionType.Emoji("❤"),
                    ReactionType.Emoji("👍"),
                    ReactionType.Emoji("🔥"),
                    ReactionType.Emoji("😂"),
                    ReactionType.Emoji("😮"),
                ),
            chosen = null,
        )

    private fun story(
        media: StoryMedia,
        caption: FormattedText = FormattedText.Empty,
        progress: Float = PREVIEW_PROGRESS,
    ) = StoryUi(
        storyId = 12,
        index = 1,
        count = 4,
        date = 0,
        ageSeconds = AGE_SECONDS,
        media = media,
        caption = caption,
        progress = { progress },
    )

    private fun content(
        media: StoryMedia,
        caption: FormattedText = FormattedText.Empty,
        current: Int = 0,
        reply: StoryReplyUi? = StoryReplyUi(),
        reactions: StoryReactionsUi? = this.reactions,
    ) = StoryViewerUiState.Content(
        posters = posters,
        current = current,
        story = story(media, caption),
        isPaused = false,
        isMuted = false,
        reply = reply,
        reactions = reactions,
    )

    /** A photo (the preview handler's color) with a caption, from a user: the reply field and reactions. */
    val photo =
        content(
            StoryMedia.Photo(fileId = 41, width = 1080, height = 1920, minithumbnail = null, isReady = true),
            caption = caption,
        )

    /** A video with sound, its first frame not yet shown, from a channel: reactions only. */
    val video =
        content(
            StoryMedia.Video(
                width = 720,
                height = 1280,
                minithumbnail = null,
                thumbnailFileId = 42,
                isReady = true,
                showsFrame = false,
                hasSound = true,
            ),
            current = 1,
            reply = null,
        )

    /** Content Pingogram cannot show. */
    val unsupported = content(StoryMedia.Unsupported, reply = null, reactions = null)

    /** The row of reactions open, one chosen. */
    val reacting =
        content(
            StoryMedia.Photo(fileId = 41, width = 1080, height = 1920, minithumbnail = null, isReady = true),
            reactions = reactions.copy(chosen = ReactionType.Emoji("🔥"), isOpen = true),
        )

    val failed = content(StoryMedia.Failed(DomainError.Network), reply = null, reactions = null)

    const val PREVIEW_PROGRESS = 0.4f

    /** Three hours. */
    private const val AGE_SECONDS = 10_800L
}

internal class StoryViewerPreviewStates : PreviewParameterProvider<StoryViewerUiState> {
    override val values =
        sequenceOf(
            StoryViewerPreviewData.photo,
            StoryViewerPreviewData.video,
            StoryViewerPreviewData.reacting,
            StoryViewerPreviewData.unsupported,
            StoryViewerPreviewData.failed,
            StoryViewerUiState.Loading,
        )
}
