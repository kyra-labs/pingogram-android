package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A conference call: an end-to-end encrypted group call outside any chat, reached by a link or an invitation
 * (`docs/adr/0025-conference-calls.md`); td_api.tl `InputGroupCall`.
 */
sealed interface ConferenceTarget {
    /** A `t.me/call/…` link, or the link a one-to-one call was upgraded to. */
    data class Link(
        val link: String,
    ) : ConferenceTarget

    /** The invitation message (`messageGroupCall`) [messageId] in chat [chatId]. */
    data class Invitation(
        val chatId: Long,
        val messageId: Long,
    ) : ConferenceTarget
}

/** What the engine joins a conference call with: its microphone's [audioSourceId] and its join [payload] (JSON). */
data class ConferenceJoinParameters(
    val audioSourceId: Int,
    val payload: String,
    val isMuted: Boolean,
    val isVideo: Boolean,
)

/** A conference call joined or created: its group call's id, and the connection parameters for the engine (JSON). */
data class ConferenceJoin(
    val groupCallId: Int,
    val params: String,
)

/**
 * The emojis everyone in conference call compares ([emojis], empty until the call's state is verified), for state
 * [generation]: two people see the same emojis only for the same generation.
 */
data class ConferenceVerification(
    val generation: Int,
    val emojis: ImmutableList<String>,
)

/** The encrypted connections of a conference call: its main one, and the one screen sharing sends on. */
enum class ConferenceChannel { Main, ScreenSharing }

/** What came of inviting someone into a conference call (td_api.tl `InviteGroupCallParticipantResult`). */
sealed interface ConferenceInviteResult {
    /** Sent as the invitation message [messageId] in their chat [chatId]. */
    data class Invited(
        val chatId: Long,
        val messageId: Long,
    ) : ConferenceInviteResult

    /** Their privacy settings do not let the user invite them. */
    data object PrivacyRestricted : ConferenceInviteResult

    data object AlreadyParticipant : ConferenceInviteResult

    /** They were removed from the call. */
    data object Banned : ConferenceInviteResult
}
