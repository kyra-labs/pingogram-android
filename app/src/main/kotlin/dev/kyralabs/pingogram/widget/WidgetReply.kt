package dev.kyralabs.pingogram.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.kyralabs.pingogram.MainActivity

/**
 * A Conversation widget's Reply (board 33): it opens the app with the widget's chat and account ([intent]), which
 * switches to that account as a notification's tap does (`AccountRestart`, through [chatOf]'s slot) and, signed in and
 * unlocked, shows the small reply sheet of the chat (owner decision 2026-11-15). Any app could send it, as the activity
 * is exported: the sheet names the chat and sends nothing until the user types and taps send.
 */
object WidgetReply {
    internal const val ACTION_REPLY = "dev.kyralabs.pingogram.action.WIDGET_REPLY"

    private const val EXTRA_CHAT_ID = "dev.kyralabs.pingogram.extra.CHAT_ID"
    private const val EXTRA_ACCOUNT = "dev.kyralabs.pingogram.extra.ACCOUNT"
    private const val NO_CHAT = 0L

    /** Opens the reply sheet of chat [chatId] of the account in [slot]; each chat's intent is its own. */
    fun intent(
        context: Context,
        chatId: Long,
        slot: Int,
    ): Intent =
        Intent(ACTION_REPLY, "pingogram-widget://reply/$slot/$chatId".toUri(), context, MainActivity::class.java)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putExtra(EXTRA_ACCOUNT, slot)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The chat [intent] replies to, with its account; null for other intents and for one Android hands over again as
     * the app is reopened from the recent apps.
     */
    fun chatOf(intent: Intent?): ConversationKey? =
        intent
            ?.takeIf { it.action == ACTION_REPLY }
            ?.takeIf { it.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 }
            ?.let { reply ->
                reply.getLongExtra(EXTRA_CHAT_ID, NO_CHAT).takeIf { it != NO_CHAT }?.let { chatId ->
                    ConversationKey(slot = reply.getIntExtra(EXTRA_ACCOUNT, 0), chatId = chatId)
                }
            }
}
