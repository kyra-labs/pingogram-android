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
 *
 * With several accounts (`docs/adr/0031-multiple-accounts.md`), each intent and shortcut id names its account's slot
 * as well ([accountOf], [chatOfShortcut]), which the app switches to before opening the chat. The first account's
 * are as they were before there were several, so the shortcuts and notifications of earlier versions open in it.
 */
object NotificationIntents {
    /**
     * Opens chat [chatId] in the app, in its task: an activity already running gets it in `onNewIntent`. Null if the
     * app has no launcher activity (never in the app; possible in tests).
     */
    fun openChat(
        context: Context,
        chatId: Long,
        slot: Int = 0,
    ): Intent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            action = Intent.ACTION_VIEW
            removeCategory(Intent.CATEGORY_LAUNCHER)
            data = chatUri(chatId, slot)
            putExtra(EXTRA_CHAT_ID, chatId)
            putExtra(EXTRA_ACCOUNT, slot)
            // Back from the chat leads to the chat list of the app's one activity, never to a second instance.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    /** [openChat] for a notification's tap; immutable, as nothing fills it in. */
    fun openChatPendingIntent(
        context: Context,
        chatId: Long,
        slot: Int = 0,
    ): PendingIntent? =
        openChat(context, chatId, slot)?.let { intent ->
            PendingIntent.getActivity(context, 0, intent, IMMUTABLE_FLAGS)
        }

    /**
     * Opens the locked chats (`docs/adr/0029-locked-chats.md`), which ask for the passcode first: the tap of their one
     * notification, which names no chat. The activity reads it back with [opensLockedChats].
     */
    fun openLockedChatsPendingIntent(
        context: Context,
        slot: Int = 0,
    ): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.action = Intent.ACTION_VIEW
            intent.removeCategory(Intent.CATEGORY_LAUNCHER)
            intent.data =
                Uri
                    .Builder()
                    .scheme(SCHEME)
                    .authority(LOCKED_CHATS_HOST)
                    // Another account's data differs, so it gets a pending intent of its own.
                    .apply { if (slot != 0) appendQueryParameter(ACCOUNT_PARAMETER, "$slot") }
                    .build()
            intent.putExtra(EXTRA_ACCOUNT, slot)
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

    /**
     * The account whose chat or locked chats [intent] opens ([chatIdOf], [opensLockedChats]): its slot, 0 for the
     * intents of versions before several accounts. Null for any other intent.
     */
    fun accountOf(intent: Intent?): Int? =
        intent?.takeIf { chatIdOf(it) != null || opensLockedChats(it) }?.getIntExtra(EXTRA_ACCOUNT, 0)

    /**
     * The id of chat [chatId]'s conversation shortcut, which its notifications name too: `chat_<id>` for the first
     * account's, `chat_<slot>_<id>` for another's.
     */
    fun shortcutId(
        chatId: Long,
        slot: Int = 0,
    ): String = if (slot == 0) "$SHORTCUT_PREFIX$chatId" else "$SHORTCUT_PREFIX${slot}_$chatId"

    /**
     * The chat of conversation shortcut [shortcutId] ([shortcutId]) and its account: what a share from a Direct Share
     * target names (`Intent.EXTRA_SHORTCUT_ID`). Null for any other id.
     */
    fun chatOfShortcut(shortcutId: String?): ShortcutChat? {
        val rest = shortcutId?.takeIf { it.startsWith(SHORTCUT_PREFIX) }?.removePrefix(SHORTCUT_PREFIX)
        val separator = rest?.indexOf('_') ?: -1
        val slot = if (separator < 0) 0 else rest?.substring(0, separator)?.toIntOrNull()
        val chatId = rest?.substring(separator + 1)?.toLongOrNull()?.takeIf { it != NO_CHAT }
        return if (slot != null && chatId != null) ShortcutChat(slot, chatId) else null
    }

    /**
     * The category of the conversation shortcuts that are Direct Share targets, which `res/xml/shortcuts.xml` of the
     * app names in its share target: the system's share sheet lists them, and a share to one names its shortcut.
     */
    const val SHARE_TARGET_CATEGORY = "dev.kyralabs.pingogram.category.SHARE_TARGET"

    private fun chatUri(
        chatId: Long,
        slot: Int,
    ): Uri =
        Uri
            .Builder()
            .scheme(SCHEME)
            .authority(CHAT_HOST)
            .appendPath("$chatId")
            .apply { if (slot != 0) appendQueryParameter(ACCOUNT_PARAMETER, "$slot") }
            .build()

    private const val IMMUTABLE_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private const val SCHEME = "pingogram"
    private const val CHAT_HOST = "chat"
    private const val LOCKED_CHATS_HOST = "locked-chats"
    private const val EXTRA_CHAT_ID = "dev.kyralabs.pingogram.extra.CHAT_ID"
    private const val EXTRA_ACCOUNT = "dev.kyralabs.pingogram.extra.ACCOUNT"
    private const val ACCOUNT_PARAMETER = "account"
    private const val SHORTCUT_PREFIX = "chat_"

    // TDLib's chat ids are never 0.
    private const val NO_CHAT = 0L
}

/** A conversation shortcut's chat ([NotificationIntents.chatOfShortcut]) and the slot of its account. */
data class ShortcutChat(
    val slot: Int,
    val chatId: Long,
)
