package dev.kyralabs.pingogram.core.domain.model

/**
 * Whom the account is notified of reactions and votes from (td_api.tl `reactionNotificationSettings`): to its
 * [messages], to its [stories], and votes in its polls ([pollVotes]); [showPreview] puts the reaction in the
 * notification. They belong to the Telegram account and follow it to its other apps.
 */
data class ReactionNotificationSettings(
    val messages: ReactionNotificationSource,
    val stories: ReactionNotificationSource,
    val pollVotes: ReactionNotificationSource,
    val showPreview: Boolean,
)

/** Whose reactions notify: nobody's, the user's contacts', or everyone's. */
enum class ReactionNotificationSource { None, Contacts, All }
