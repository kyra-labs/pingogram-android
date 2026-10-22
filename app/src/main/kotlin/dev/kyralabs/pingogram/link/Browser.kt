package dev.kyralabs.pingogram.link

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.kyralabs.pingogram.MainActivity

/**
 * Opens [url] in a browser, never in Pingogram, whose intent filters take `t.me` links too
 * (`docs/adr/0013-deep-links.md`): the view intent goes to the apps that are browsers (a selector for
 * [Intent.CATEGORY_APP_BROWSER], which resolves to the default browser); without one, to a chooser of every other app
 * that opens it, Pingogram left out. False if no app opens it.
 */
internal fun Context.openInBrowser(url: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val browser =
        Intent(
            view,
        ).apply { selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER) }
    if (tryStart(browser)) return true
    val others =
        Intent
            .createChooser(view, null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, MainActivity::class.java)))
    return tryStart(others)
}

private fun Context.tryStart(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
