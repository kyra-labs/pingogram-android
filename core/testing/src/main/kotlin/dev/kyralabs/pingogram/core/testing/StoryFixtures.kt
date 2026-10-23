package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/** TdApi stories for tests of the stories repository and screens, with the defaults of a photo story by user 42. */
object StoryFixtures {
    fun storyInfo(
        storyId: Int = 1,
        date: Int = 1_700_000_000,
        isForCloseFriends: Boolean = false,
        isLive: Boolean = false,
    ) = TdApi.StoryInfo(storyId, date, isForCloseFriends, isLive)

    /** Chat [chatId]'s active stories [storyIds] in [list] at [order], seen up to [maxReadStoryId]. */
    fun chatActiveStories(
        chatId: Long = 42,
        list: TdApi.StoryList? = TdApi.StoryListMain(),
        order: Long = 1,
        maxReadStoryId: Int = 0,
        storyIds: List<Int> = listOf(1),
    ) = TdApi.ChatActiveStories(
        chatId,
        list,
        order,
        true,
        maxReadStoryId,
        storyIds.map { storyInfo(storyId = it) }.toTypedArray(),
    )

    fun updateChatActiveStories(activeStories: TdApi.ChatActiveStories = chatActiveStories()) =
        TdApi.UpdateChatActiveStories(activeStories)

    fun photoContent(photo: TdApi.Photo = TdApiFixtures.photo()) = TdApi.StoryContentPhoto(photo)

    fun videoContent(
        duration: Double = 12.5,
        file: TdApi.File = TdApiFixtures.file(id = 31, size = 2_000_000),
        isAnimation: Boolean = false,
    ) = TdApi.StoryContentVideo(
        TdApi.StoryVideo(
            duration,
            720,
            1280,
            false,
            isAnimation,
            TdApiFixtures.minithumbnail(),
            TdApiFixtures.thumbnail(),
            0,
            0.0,
            file,
        ),
        null,
    )

    /** Story [id] of chat [posterChatId]; the user may reply to it and has not reacted. */
    fun story(
        id: Int = 1,
        posterChatId: Long = 42,
        date: Int = 1_700_000_000,
        content: TdApi.StoryContent = photoContent(),
        caption: String = "",
        canBeReplied: Boolean = true,
        chosenReaction: TdApi.ReactionType? = null,
    ) = TdApi.Story().apply {
        this.id = id
        this.posterChatId = posterChatId
        posterId = TdApi.MessageSenderChat(posterChatId)
        this.date = date
        this.canBeReplied = canBeReplied
        chosenReactionType = chosenReaction
        privacySettings = TdApi.StoryPrivacySettingsEveryone(longArrayOf())
        this.content = content
        areas = emptyArray()
        this.caption = TdApiFixtures.formattedText(caption)
        albumIds = intArrayOf()
    }

    fun updateStory(story: TdApi.Story = story()) = TdApi.UpdateStory(story)

    fun updateStoryDeleted(
        posterChatId: Long = 42,
        storyId: Int = 1,
    ) = TdApi.UpdateStoryDeleted(posterChatId, storyId)

    /** The reactions TDLib offers for stories: [emoji] (the first [premiumOnly] of them need Telegram Premium). */
    fun availableReactions(
        emoji: List<String> = listOf("❤", "👍", "🔥"),
        premiumOnly: Int = 0,
    ) = TdApi.AvailableReactions(
        emoji
            .mapIndexed { index, text -> TdApi.AvailableReaction(TdApi.ReactionTypeEmoji(text), index < premiumOnly) }
            .toTypedArray(),
        arrayOf(TdApi.AvailableReaction(TdApi.ReactionTypeCustomEmoji(5), false)),
        emptyArray(),
        true,
        false,
        null,
    )
}
