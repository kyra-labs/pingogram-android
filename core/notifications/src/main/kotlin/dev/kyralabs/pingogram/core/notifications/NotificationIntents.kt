package dev.kyralabs.pingogram.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The intents behind notifications and conversation shortcuts, and the shortcuts' ids. Opening a chat starts the
 * app's launcher activity with [Intent.ACTION_VIEW], a `pingogram://chat/<id>` data URI (so each chat gets a pending
 * intent of its own) and the chat id as an extra; the activity reads it back with [chatIdOf]. Explicit, so no other
 * app receives it, and the URI's scheme needs no intent filter.
 */
object NotificationIntents {
    /**
     * Opens chat [chatId] in the app, in its task: an activity already running gets it in `onNewIntent`. Null if the
     * app has no launcher activity (never in the app; possible in tests).
     */
    fun openChat(
        context: Context,
        chatId: Long,
    ): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            action = Intent.ACTION_VIEW
            removeCategory(Intent.CATEGORY_LAUNCHER)
            data = chatUri(chatId)
            putExtra(EXTRA_CHAT_ID, chatId)
            // Back from the chat leads to the chat list of the app's one activity, never to a second instance.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    /** [openChat] for a notification's tap; immutable, as nothing fills it in. */
    fun openChatPendingIntent(
        context: Context,
        chatId: Long,
    ): PendingIntent? =
        openChat(context, chatId)?.let { intent ->
            PendingIntent.getActivity(context, 0, intent, IMMUTABLE_FLAGS)
        }

    /**
     * Opens the locked chats (`docs/adr/0029-locked-chats.md`), which ask for the passcode first: the tap of their one
     * notification, which names no chat. The activity reads it back with [opensLockedChats].
     */
    fun openLockedChatsPendingIntent(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.action = Intent.ACTION_VIEW
            intent.removeCategory(Intent.CATEGORY_LAUNCHER)
            intent.data =
                Uri
                    .Builder()
                    .scheme(SCHEME)
                    .authority(LOCKED_CHATS_HOST)
                    .build()
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            PendingIntent.getActivity(context, 0, intent, IMMUTABLE_FLAGS)
        }

    /** Whether [intent] opens the locked chats ([openLockedChatsPendingIntent]), not replayed from the recent apps. */
    fun opensLockedChats(intent: Intent?): Boolean =
        intent != null &&
            intent.action == Intent.ACTION_VIEW &&
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 &&
            intent.data?.scheme == SCHEME &&
            intent.data?.host == LOCKED_CHATS_HOST

    /** Opens the app where it was, for the summary of all notifications. */
    fun openAppPendingIntent(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            PendingIntent.getActivity(context, 0, intent, IMMUTABLE_FLAGS)
        }

    /**
     * The chat that [intent] opens: one from [openChat], not replayed from the recent apps (Android then delivers the
     * intent that started the task again). Null for any other intent.
     */
    fun chatIdOf(intent: Intent?): Long? {
        val opensChat =
            intent != null &&
                intent.action == Intent.ACTION_VIEW &&
                intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 &&
                intent.data?.scheme == SCHEME &&
                intent.data?.host == CHAT_HOST
        return intent?.takeIf { opensChat }?.getLongExtra(EXTRA_CHAT_ID, NO_CHAT)?.takeIf { it != NO_CHAT }
    }

    /** The id of chat [chatId]'s conversation shortcut, which its notifications name too. */
    fun shortcutId(chatId: Long): String = "$SHORTCUT_PREFIX$chatId"

    /**
     * The chat of conversation shortcut [shortcutId] ([shortcutId]): what a share from a Direct Share target names
     * (`Intent.EXTRA_SHORTCUT_ID`). Null for any other id.
     */
    fun chatIdOfShortcut(shortcutId: String?): Long? =
        shortcutId
            ?.takeIf { it.startsWith(SHORTCUT_PREFIX) }
            ?.removePrefix(SHORTCUT_PREFIX)
            ?.toLongOrNull()
            ?.takeIf { it != NO_CHAT }

    /**
     * The category of the conversation shortcuts that are Direct Share targets, which `res/xml/shortcuts.xml` of the
     * app names in its share target: the system's share sheet lists them, and a share to one names its shortcut.
     */
    const val SHARE_TARGET_CATEGORY = "dev.kyralabs.pingogram.category.SHARE_TARGET"

    private fun chatUri(chatId: Long): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority(CHAT_HOST)
            .appendPath("$chatId")
            .build()

    private const val IMMUTABLE_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private const val SCHEME = "pingogram"
    private const val CHAT_HOST = "chat"
    private const val LOCKED_CHATS_HOST = "locked-chats"
    private const val EXTRA_CHAT_ID = "dev.kyralabs.pingogram.extra.CHAT_ID"
    private const val SHORTCUT_PREFIX = "chat_"

    // TDLib's chat ids are never 0.
    private const val NO_CHAT = 0L
}
