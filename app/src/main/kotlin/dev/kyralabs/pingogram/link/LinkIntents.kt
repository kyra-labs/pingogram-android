package dev.kyralabs.pingogram.link

import android.content.Intent
import dev.kyralabs.pingogram.core.domain.model.isTelegramLink

/**
 * The Telegram link [intent] asks Pingogram to open: the data of a view intent that the manifest's filters let in
 * (`tg:`, `t.me`, `telegram.me`, `telegram.dog`), checked again here, since any app may send an explicit intent with
 * any data. Null for other intents, and for one the system hands over again as the app is reopened from the recent apps
 * ([Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY]), which would open the link a second time.
 */
internal fun linkOf(intent: Intent?): String? {
    val view = intent?.takeIf { it.action == Intent.ACTION_VIEW } ?: return null
    val replayed = view.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
    return view.dataString?.takeIf { !replayed && isTelegramLink(it) }
}
