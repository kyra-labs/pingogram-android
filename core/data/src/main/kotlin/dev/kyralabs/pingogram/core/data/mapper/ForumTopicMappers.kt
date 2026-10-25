package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ForumTopic
import dev.kyralabs.pingogram.core.domain.model.ForumTopicIcon
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import org.drinkless.tdlib.TdApi

/** The forum topic or message thread; null for the topics of other chats (Saved Messages, direct messages). */
internal fun TdApi.MessageTopic.toMessageTopic(): MessageTopic? =
    when (this) {
        is TdApi.MessageTopicForum -> MessageTopic.Forum(forumTopicId)
        is TdApi.MessageTopicThread -> MessageTopic.Thread(messageThreadId)
        else -> null
    }

internal fun MessageTopic.toTdMessageTopic(): TdApi.MessageTopic =
    when (this) {
        is MessageTopic.Forum -> TdApi.MessageTopicForum(topicId)
        is MessageTopic.Thread -> TdApi.MessageTopicThread(threadId)
    }

internal fun TdApi.ForumTopic.toForumTopic(): ForumTopic =
    ForumTopic(
        chatId = info.chatId,
        id = info.forumTopicId,
        name = info.name,
        icon = info.icon.toForumTopicIcon(),
        isGeneral = info.isGeneral,
        isClosed = info.isClosed,
        isHidden = info.isHidden,
        isPinned = isPinned,
        lastMessage = lastMessage?.toMessage(),
        order = order,
        unreadCount = unreadCount,
        unreadMentionCount = unreadMentionCount,
        lastReadInboxMessageId = lastReadInboxMessageId,
        lastReadOutboxMessageId = lastReadOutboxMessageId,
        isOutgoing = info.isOutgoing,
    )

/** [this] topic with what [info] says now: its name, icon and state. */
internal fun ForumTopic.withInfo(info: TdApi.ForumTopicInfo): ForumTopic =
    copy(
        name = info.name,
        icon = info.icon.toForumTopicIcon(),
        isGeneral = info.isGeneral,
        isClosed = info.isClosed,
        isHidden = info.isHidden,
        isOutgoing = info.isOutgoing,
    )

internal fun TdApi.ForumTopicIcon.toForumTopicIcon() = ForumTopicIcon(topicColor(color), customEmojiId)

/**
 * The [TopicColor] nearest to [rgb] by hue: Telegram's six topic colors (0x6FB9F0 blue, 0xFFD67E yellow, 0xCB86DB
 * violet, 0x8EEE98 green, 0xFF93B2 rose, 0xFB6F5F red), and anything else by the nearest of their hues.
 */
internal fun topicColor(rgb: Int): TopicColor {
    val hue = hueOf(rgb)
    return TOPIC_HUES.minBy { (_, reference) -> hueDistance(hue, reference) }.first
}

/** Telegram's own color for [this] topic color, which `createForumTopic` takes as a new topic's icon. */
internal fun TopicColor.toTdColor(): Int =
    when (this) {
        TopicColor.Blue -> TELEGRAM_BLUE
        TopicColor.Yellow -> TELEGRAM_YELLOW
        TopicColor.Violet -> TELEGRAM_VIOLET
        TopicColor.Green -> TELEGRAM_GREEN
        TopicColor.Rose -> TELEGRAM_ROSE
        TopicColor.Red -> TELEGRAM_RED
    }

private fun hueOf(rgb: Int): Float {
    val red = (rgb shr RED_SHIFT and BYTE) / MAX
    val green = (rgb shr GREEN_SHIFT and BYTE) / MAX
    val blue = (rgb and BYTE) / MAX
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    return when {
        delta == 0f -> 0f
        max == red -> DEGREES_PER_SIXTH * (((green - blue) / delta).mod(SIXTHS_BEFORE_RED))
        max == green -> DEGREES_PER_SIXTH * ((blue - red) / delta + GREEN_SIXTHS)
        else -> DEGREES_PER_SIXTH * ((red - green) / delta + BLUE_SIXTHS)
    }
}

private fun hueDistance(
    a: Float,
    b: Float,
): Float {
    val distance = kotlin.math.abs(a - b) % FULL_TURN
    return minOf(distance, FULL_TURN - distance)
}

/** The hues of Telegram's topic colors. */
private val TOPIC_HUES =
    listOf(
        TopicColor.Blue to hueOf(TELEGRAM_BLUE),
        TopicColor.Yellow to hueOf(TELEGRAM_YELLOW),
        TopicColor.Violet to hueOf(TELEGRAM_VIOLET),
        TopicColor.Green to hueOf(TELEGRAM_GREEN),
        TopicColor.Rose to hueOf(TELEGRAM_ROSE),
        TopicColor.Red to hueOf(TELEGRAM_RED),
    )

private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BYTE = 0xFF
private const val MAX = 255f
private const val DEGREES_PER_SIXTH = 60f
private const val SIXTHS_BEFORE_RED = 6f
private const val FULL_TURN = 360f
private const val GREEN_SIXTHS = 2f
private const val BLUE_SIXTHS = 4f

// Telegram's topic colors (td_api.tl forumTopicIcon.color): matched to the app's color roles and sent with a new topic,
// never drawn.
private const val TELEGRAM_BLUE = 0x6FB9F0
private const val TELEGRAM_YELLOW = 0xFFD67E
private const val TELEGRAM_VIOLET = 0xCB86DB
private const val TELEGRAM_GREEN = 0x8EEE98
private const val TELEGRAM_ROSE = 0xFF93B2
private const val TELEGRAM_RED = 0xFB6F5F
