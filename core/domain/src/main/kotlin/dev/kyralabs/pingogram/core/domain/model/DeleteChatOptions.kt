package dev.kyralabs.pingogram.core.domain.model

/**
 * What the user can choose when deleting a chat from the chat list (`REQUIREMENTS.md` FR-L4), from
 * `ChatRepository.deleteOptions`. TDLib reports no change of what a chat allows, so it is read when the user asks.
 */
data class DeleteChatOptions(
    val action: DeleteChatAction,
    /**
     * The chat can also be deleted for everyone: for the other user of a private chat, on both sides of a secret chat,
     * or for every member of a group or channel the current user owns, which deletes the group or channel itself.
     */
    val canDeleteForEveryone: Boolean,
)

/** What deleting a chat does for the current user. */
enum class DeleteChatAction {
    /** Deletes the conversation: a private or secret chat, or a group or channel the user is no longer in. */
    Delete,

    /**
     * Leaves the group or channel, which removes it from the chat list. A basic group's history is deleted too, as a
     * basic group stays listed after leaving it.
     */
    Leave,
}
