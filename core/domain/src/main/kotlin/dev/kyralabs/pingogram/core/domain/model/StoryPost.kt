package dev.kyralabs.pingogram.core.domain.model

import dev.kyralabs.pingogram.core.common.result.DomainError

/**
 * Who sees a story the user posts (`docs/adr/0036-posting-stories.md`): everybody, the user's contacts, their close
 * friends (the list they keep on Telegram), or only [Selected] users. Exceptions to the first two are not offered yet.
 */
sealed interface StoryPrivacy {
    data object Everyone : StoryPrivacy

    data object Contacts : StoryPrivacy

    data object CloseFriends : StoryPrivacy

    data class Selected(
        val userIds: List<Long>,
    ) : StoryPrivacy
}

/**
 * Whether the user may post a story now (td_api.tl `canPostStory`), and why not: Telegram's limits, which a Telegram
 * Premium subscription raises. Pingogram says why, and never offers the subscription (`CLAUDE.md` rule 3).
 */
sealed interface StoryPostAbility {
    /** Posting is allowed; [storiesLeft] more stories may be posted this way before a limit. */
    data class Allowed(
        val storiesLeft: Int,
    ) : StoryPostAbility

    /** As many stories are active as Telegram allows; one has to expire or be deleted first. */
    data object TooManyActive : StoryPostAbility

    /** The week's limit is reached; posting is allowed again in [retryAfterSeconds]. */
    data class WeeklyLimit(
        val retryAfterSeconds: Int,
    ) : StoryPostAbility

    /** The month's limit is reached; posting is allowed again in [retryAfterSeconds]. */
    data class MonthlyLimit(
        val retryAfterSeconds: Int,
    ) : StoryPostAbility

    /** Telegram lets only its Premium subscribers post here. */
    data object PremiumNeeded : StoryPostAbility

    /** A channel needs more boosts before it may post stories. */
    data object BoostNeeded : StoryPostAbility

    /** The user's live story goes on; no other story may be posted meanwhile. */
    data object LiveStoryActive : StoryPostAbility

    /** Telegram does not allow it, for a reason this version does not know. */
    data object NotAllowed : StoryPostAbility
}

/**
 * A story of the user's that TDLib is still posting (board 10c): [storyId] is the temporary id TDLib gave it, its file
 * [uploadedBytes] of [sizeBytes] sent (the size 0 while TDLib does not know it yet), and once TDLib gave up, why
 * ([failure]). It leaves the list once posted, or cancelled.
 */
data class StoryUpload(
    val storyId: Int,
    val uploadedBytes: Long,
    val sizeBytes: Long,
    val failure: DomainError? = null,
)
