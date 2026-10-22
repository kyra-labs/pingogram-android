package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A group the user just created (`REQUIREMENTS.md` FR-P4, `ChatCreationRepository.createGroup`): its chat [chatId],
 * and the people the user picked whom Telegram did not add ([notAddedUserIds]), usually because their privacy
 * settings let only their contacts add them to groups. They can still join through an invite link.
 */
data class CreatedGroup(
    val chatId: Long,
    val notAddedUserIds: ImmutableList<Long>,
)

/** The limits Telegram sets on new groups and channels, in Unicode characters (code points), as TDLib counts them. */
object ChatCreationLimits {
    /** The longest title; TDLib cuts longer ones (`MAX_TITLE_LENGTH`, td/telegram/ChatManager.cpp). */
    const val TITLE_LENGTH_MAX = 128

    /** The longest channel description (`MAX_DESCRIPTION_LENGTH`, td/telegram/ChatManager.cpp). */
    const val DESCRIPTION_LENGTH_MAX = 255

    /**
     * The most people a basic group holds, its creator included (TDLib's option `basic_group_size_max`, 200 for every
     * account); a new group for more is created as a supergroup.
     */
    const val BASIC_GROUP_SIZE_MAX = 200

    /**
     * The most people the user may pick to add when creating a group or channel: Telegram adds at most 200 people by
     * invitation, the rest join through an invite link.
     */
    const val MEMBERS_TO_ADD_MAX = 200
}

/**
 * How long messages stay in a new group before they are deleted for everyone (td_api.tl `message_auto_delete_time`).
 */
enum class MessageAutoDelete(
    val seconds: Int,
) {
    Off(0),
    Day(DAY_SECONDS),
    Week(WEEK_SECONDS),
    Month(MONTH_SECONDS),
}

private const val DAY_SECONDS = 86_400
private const val WEEK_SECONDS = 604_800

/** 31 days, as Telegram's own apps offer it. */
private const val MONTH_SECONDS = 2_678_400
