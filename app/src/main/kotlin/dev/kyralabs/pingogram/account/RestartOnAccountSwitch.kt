package dev.kyralabs.pingogram.account

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.kyralabs.pingogram.core.data.di.Accounts
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Opens the app again for the other account when the shown one changes (`docs/adr/0031-multiple-accounts.md`): a new
 * task, so no screen, view model or saved state of the account shown before is kept, as each took that account's
 * repositories when it was made. The activity of the new task starts over at the chat list or the login steps.
 */
internal fun ComponentActivity.restartOnAccountSwitch(accounts: Accounts) {
    lifecycleScope.launch {
        accounts.awaitLoaded()
        accounts.registry
            .map { it.active }
            .distinctUntilChanged()
            .drop(1)
            .first()
        val intent =
            Intent(this@restartOnAccountSwitch, this@restartOnAccountSwitch::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
