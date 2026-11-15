package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.Story
import dev.kyralabs.pingogram.core.domain.model.StoryContent
import dev.kyralabs.pingogram.core.domain.model.StoryInfo
import dev.kyralabs.pingogram.core.domain.model.StoryInteractionInfo
import dev.kyralabs.pingogram.core.domain.model.StoryList
import dev.kyralabs.pingogram.core.domain.model.StoryViewer
import dev.kyralabs.pingogram.core.domain.model.StoryViewersPage
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
        canBeDeleted = canBeDeleted,
        interactions = interactionInfo?.takeIf { canGetInteractions }?.toInteractionInfo(),
    )

private fun TdApi.StoryInteractionInfo.toInteractionInfo() =
    StoryInteractionInfo(viewCount, reactionCount, recentViewerUserIds.toList().toImmutableList())

/** Who saw the user's own story: users only (a chat's repost or forward is left out), and the page's totals. */
internal fun TdApi.StoryInteractions.toViewersPage(): StoryViewersPage =
    StoryViewersPage(
        totalCount = totalCount,
        reactionCount = totalReactionCount,
        viewers =
            interactions
                .mapNotNull { interaction ->
                    val user = interaction.actorId as? TdApi.MessageSenderUser ?: return@mapNotNull null
                    val view = interaction.type as? TdApi.StoryInteractionTypeView ?: return@mapNotNull null
                    StoryViewer(
                        userId = user.userId,
                        date = interaction.interactionDate.toLong(),
                        reaction = view.chosenReactionType?.toReactionType()?.takeUnless { it == ReactionType.Paid },
                    )
                }.toImmutableList(),
        nextOffset = nextOffset.takeIf { it.isNotEmpty() },
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
            StoryContent.Live(groupCallId)
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
