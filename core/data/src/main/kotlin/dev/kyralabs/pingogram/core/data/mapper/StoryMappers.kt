package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryContent
import dev.kyralabs.pingogram.core.domain.model.StoryInfo
import dev.kyralabs.pingogram.core.domain.model.StoryList
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi
import kotlin.math.roundToLong

/** A chat's active stories, oldest first, as TDLib lists them. */
internal fun TdApi.ChatActiveStories.toActiveStories(): ActiveStories =
    ActiveStories(
        chatId = chatId,
        list = list?.toStoryList(),
        order = order,
        maxReadStoryId = maxReadStoryId,
        stories =
            stories
                .orEmpty()
                .map { StoryInfo(it.storyId, it.date.toLong(), it.isForCloseFriends, it.isLive) }
                .sortedBy { it.id }
                .toImmutableList(),
    )

internal fun TdApi.StoryList.toStoryList(): StoryList? =
    when (this) {
        is TdApi.StoryListMain -> StoryList.Main
        is TdApi.StoryListArchive -> StoryList.Archive
        else -> null
    }

internal fun StoryList.toTdStoryList(): TdApi.StoryList =
    when (this) {
        StoryList.Main -> TdApi.StoryListMain()
        StoryList.Archive -> TdApi.StoryListArchive()
    }

/** The story as the viewer shows it; its areas, privacy settings and interactions are left out. */
internal fun TdApi.Story.toStory(): Story =
    Story(
        id = id,
        posterChatId = posterChatId,
        date = date.toLong(),
        content = content.toStoryContent(),
        caption = caption?.toFormattedText() ?: FormattedText.Empty,
        canBeReplied = canBeReplied,
        chosenReaction = chosenReactionType?.toReactionType()?.takeUnless { it == ReactionType.Paid },
    )

private fun TdApi.StoryContent?.toStoryContent(): StoryContent =
    when (this) {
        is TdApi.StoryContentPhoto -> {
            StoryContent.Photo(photo.toPhoto())
        }

        is TdApi.StoryContentVideo -> {
            StoryContent.Video(
                durationMillis = (video.duration * MILLIS_PER_SECOND).roundToLong(),
                width = video.width,
                height = video.height,
                isAnimation = video.isAnimation,
                minithumbnail = video.minithumbnail?.toMinithumbnail(),
                thumbnail = video.thumbnail?.toThumbnail(),
                file = video.video.toFileRef(),
            )
        }

        is TdApi.StoryContentLive -> {
            StoryContent.Live
        }

        // storyContentUnsupported, and kinds a newer TDLib adds.
        else -> {
            StoryContent.Unsupported
        }
    }

/** Null for the paid reaction, which has its own functions; for messages' reactions and stories' alike. */
internal fun ReactionType.toTdReactionType(): TdApi.ReactionType? =
    when (this) {
        is ReactionType.Emoji -> TdApi.ReactionTypeEmoji(emoji)
        is ReactionType.CustomEmoji -> TdApi.ReactionTypeCustomEmoji(customEmojiId)
        ReactionType.Paid -> null
    }

private const val MILLIS_PER_SECOND = 1_000.0
