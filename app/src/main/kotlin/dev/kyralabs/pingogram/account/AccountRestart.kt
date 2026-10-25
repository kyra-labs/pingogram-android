package dev.kyralabs.pingogram.account

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import dev.kyralabs.pingogram.core.data.account.AccountRegistry
import dev.kyralabs.pingogram.core.data.di.Accounts
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Opens the app again for the other account when the shown one changes (`docs/adr/0031-multiple-accounts.md`): a new
 * task, so no screen, view model or saved state of the account shown before is kept, as each took that account's
 * repositories when it was made. The activity of the new task starts over at the chat list or the login steps.
 *
 * An intent of another account ([accountOf]: a notification's, a conversation shortcut's, a share to a Direct Share
 * target) switches to that account first, and the app opens again with that intent, which then opens its chat there
 * (behind the passcode lock, which is the app's). One of an account no longer on the device opens nothing. Every
 * other intent (a Telegram link, a share from the share sheet) is the shown account's, handed to [handle] at once.
 */
internal class AccountRestart(
    private val scope: CoroutineScope,
    private val accounts: AccountSwitching,
    private val handle: (Intent?) -> Unit,
    /** Opens the app again in a new task with the intent, finishing this activity. */
    private val restart: (Intent?) -> Unit,
) {
    constructor(activity: ComponentActivity, accounts: Accounts, handle: (Intent?) -> Unit) : this(
        scope = activity.lifecycleScope,
        accounts = accounts.switching(),
        handle = handle,
        restart = { next ->
            val intent =
                next ?: Intent(activity, activity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(RESTART_FLAGS)
            activity.startActivity(intent)
            activity.finish()
        },
    )

    /** The intent the app opens again with after a switch for it; the launcher's otherwise. */
    private var next: Intent? = null

    /** Follows the shown account; [created] is the intent a new activity (not a restored one) was created with. */
    fun start(created: Intent?) {
        if (accountOf(created) == null) handle(created)
        scope.launch {
            accounts.awaitLoaded()
            val shown = accounts.registry.value.active
            if (accountOf(created) != null) open(created, shown)
            accounts.registry.first { it.active != shown }
            restart(next)
        }
    }

    /** An intent that reached the running activity ([ComponentActivity.onNewIntent]). */
    fun onNewIntent(intent: Intent) {
        if (accountOf(intent) == null) {
            handle(intent)
            return
        }
        scope.launch {
            accounts.awaitLoaded()
            open(intent, accounts.registry.value.active)
        }
    }

    /** Hands [intent] on in the shown account, or switches to its own account, which opens the app again with it. */
    private fun open(
        intent: Intent?,
        shown: Int,
    ) {
        val slot = accountOf(intent) ?: return
        when {
            slot == shown -> {
                handle(intent)
            }

            intent != null && slot in accounts.registry.value.slots -> {
                next = Intent(intent).setFlags(RESTART_FLAGS or (intent.flags and GRANT_FLAGS))
                accounts.switchTo(slot)
            }
        }
    }

    companion object {
        /** A new task, so nothing of the account shown before stays. */
        const val RESTART_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        /** A share's read grants, which the app passes on to itself, so the files stay readable after the restart. */
        private const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}

/** What [AccountRestart] needs of [Accounts]: which accounts there are and the shown one, and switching. */
internal interface AccountSwitching {
    val registry: StateFlow<AccountRegistry>

    suspend fun awaitLoaded()

    fun switchTo(slot: Int)
}

private fun Accounts.switching(): AccountSwitching =
    object : AccountSwitching {
        override val registry get() = this@switching.registry

        override suspend fun awaitLoaded() = this@switching.awaitLoaded()

        override fun switchTo(slot: Int) = this@switching.switchTo(slot)
    }

/**
 * The account [intent] belongs to: a notification's or a conversation shortcut's ([NotificationIntents.accountOf]),
 * or a share's to a Direct Share target ([NotificationIntents.accountOfShortcut]). Null for intents of no account in
 * particular, which open in the shown one.
 */
internal fun accountOf(intent: Intent?): Int? {
    if (intent == null) return null
    NotificationIntents.accountOf(intent)?.let { return it }
    val sharing = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
    val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
    if (!sharing || fromHistory) return null
    return NotificationIntents.accountOfShortcut(intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID))
}
