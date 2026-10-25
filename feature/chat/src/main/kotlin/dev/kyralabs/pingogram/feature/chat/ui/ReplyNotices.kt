package dev.kyralabs.pingogram.feature.chat.ui

import android.content.Context
import android.content.res.Resources
import android.os.Build
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ReplaceMedia

/**
 * What [effect] does on screen and the snackbar it leaves (empty for none): a message's link goes to the clipboard
 * through [copyText], with "Link copied" before Android 13, which confirms copies itself; the replacement's picker
 * opens through [pick], or the snackbar says no app can; a selection that is not the message's text says so.
 */
internal fun Context.replyNotice(
    effect: ReplyEffect,
    resources: Resources,
    copyText: Context.(text: String) -> Unit,
    pick: (ReplaceMedia) -> Boolean,
): String =
    when (effect) {
        is ReplyEffect.CopyLink -> {
            copyText(effect.link)
            val confirmsItself = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            if (confirmsItself) "" else resources.getString(R.string.message_link_copied)
        }

        is ReplyEffect.Pick -> {
            if (pick(effect.kind)) "" else resources.getString(R.string.attach_no_picker_app)
        }

        ReplyEffect.QuoteUnavailable -> {
            resources.getString(R.string.quote_unavailable)
        }
    }
