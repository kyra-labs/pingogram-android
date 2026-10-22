package dev.kyralabs.pingogram.core.domain.model

/**
 * How far a channel or supergroup has been boosted by Telegram Premium subscribers (`REQUIREMENTS.md` FR-P5, read
 * only), from TDLib's `getChatBoostStatus`. Pingogram shows it and never boosts: that needs Telegram Premium, which it
 * does not offer (`REQUIREMENTS.md` §1.2).
 *
 * @property level the chat's boost level; 0 before its first.
 * @property boostCount the boosts the chat has, gift codes included.
 * @property currentLevelBoostCount the boosts [level] took.
 * @property nextLevelBoostCount the boosts the next level takes; 0 at the highest level.
 * @property premiumMemberCount the members (subscribers) with Telegram Premium, an estimate for large chats.
 * @property premiumMemberPercentage their share of the members, 0–100.
 * @property boostedByUser the user boosts the chat already, from another app.
 * @property unusedBoosts the user's boosts that boost no chat yet; 0 without Telegram Premium. Said, never offered.
 */
data class ChatBoostStatus(
    val level: Int,
    val boostCount: Int,
    val currentLevelBoostCount: Int,
    val nextLevelBoostCount: Int,
    val premiumMemberCount: Int,
    val premiumMemberPercentage: Double,
    val boostedByUser: Boolean = false,
    val unusedBoosts: Int = 0,
) {
    /** The chat has reached the highest level Telegram has. */
    val isMaxLevel: Boolean get() = nextLevelBoostCount == 0

    /** How much of the way from [level] to the next one the chat has come, 0–1; 1 at the highest level. */
    val levelProgress: Float
        get() {
            val span = nextLevelBoostCount - currentLevelBoostCount
            if (isMaxLevel || span <= 0) return 1f
            return ((boostCount - currentLevelBoostCount).toFloat() / span).coerceIn(0f, 1f)
        }

    /** The boosts the chat still needs for the next level; 0 at the highest level. */
    val boostsToNextLevel: Int get() = if (isMaxLevel) 0 else (nextLevelBoostCount - boostCount).coerceAtLeast(0)
}
