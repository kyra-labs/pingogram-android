package dev.kyralabs.pingogram.navigation

/**
 * A chat a notification or a conversation shortcut asked to open, at [messageId] where it names one (a reminder's).
 */
data class ChatToOpen(
    val chatId: Long,
    val messageId: Long? = null,
)
