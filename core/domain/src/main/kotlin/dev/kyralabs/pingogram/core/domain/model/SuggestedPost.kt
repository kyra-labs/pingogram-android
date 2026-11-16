package dev.kyralabs.pingogram.core.domain.model

/**
 * A post a subscriber suggested to a channel through its direct messages (td_api.tl `suggestedPostInfo`): what they
 * offer for it ([price], none for a free one; Pingogram pays and takes no offer, `REQUIREMENTS.md` §1.2), when it is
 * to be published ([sendDate], Unix seconds, 0 when the channel's administrators choose), where it stands ([state]),
 * and what TDLib lets the user do with it ([canBeApproved], [canBeDeclined]: an administrator, while it waits).
 */
data class SuggestedPost(
    val price: SuggestedPostPrice? = null,
    val sendDate: Long = 0L,
    val state: SuggestedPostState = SuggestedPostState.Pending,
    val canBeApproved: Boolean = false,
    val canBeDeclined: Boolean = false,
)

/** What a subscriber offers for a suggested post (td_api.tl `SuggestedPostPrice`). */
sealed interface SuggestedPostPrice {
    /** [count] Telegram Stars. */
    data class Stars(
        val count: Long,
    ) : SuggestedPostPrice

    /** Toncoin, in hundredths ([cents]). */
    data class Toncoin(
        val cents: Long,
    ) : SuggestedPostPrice
}

/** Where a suggested post stands (td_api.tl `SuggestedPostState`). */
enum class SuggestedPostState {
    Pending,
    Approved,
    Declined,
}
