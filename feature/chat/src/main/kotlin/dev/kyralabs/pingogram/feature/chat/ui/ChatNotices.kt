package dev.kyralabs.pingogram.feature.chat.ui

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.voice.voiceNoticeActionLabel

/**
 * Shows [message], the snackbar of [effect], with the action its effect has ([actionLabelOf]), which runs [onAction]
 * with the effect when tapped: after forwarding to one other chat ([ChatEffect.Forwarded.openChatId]) it opens the
 * chat, after saving one file ([SelectionEffect.Saved.open]) it opens the file (a snackbar follows if no app can, the
 * text [onAction] returns), before the storage permission it goes
 * on to ask for it, after a refusal it opens the app's settings; the same for the microphone a voice message needs.
 * A snackbar with an action stays until it is dismissed or replaced (Material's default). Returns once the snackbar
 * is gone.
 */
internal suspend fun SnackbarHostState.showNotice(
    message: String,
    effect: ChatEffect,
    resources: Resources,
    onAction: (ChatEffect) -> String,
) {
    val result = showSnackbar(message, actionLabelOf(effect)?.let(resources::getString))
    val next = if (result == SnackbarResult.ActionPerformed) onAction(effect) else ""
    if (next.isNotEmpty()) showSnackbar(next)
}

/** The action of [effect]'s snackbar; null where it has none. */
@StringRes
private fun actionLabelOf(effect: ChatEffect): Int? =
    when {
        effect is ChatEffect.Forwarded && effect.openChatId != null -> R.string.forward_open_chat
        effect is SelectionEffect.Saved && effect.open != null -> R.string.save_open
        effect == SelectionEffect.RequestStoragePermission -> R.string.save_storage_continue
        effect == SelectionEffect.StoragePermissionDenied -> R.string.save_storage_settings
        effect is VoiceEffect -> voiceNoticeActionLabel(effect)
        else -> null
    }

/**
 * The text of the snackbar of a [SelectionEffect]; empty where none shows. Copied messages go to the clipboard
 * through [copyText], under their senders and times ([copiedText]), with a snackbar before Android 13 ([copiedNotice]);
 * the storage permission is asked for at once through [requestStoragePermission] unless [showsStorageRationale],
 * which explains first.
 */
internal fun Context.selectionNotice(
    effect: SelectionEffect,
    resources: Resources,
    copyText: Context.(text: String) -> Unit,
    showsStorageRationale: Boolean,
    requestStoragePermission: () -> Unit,
): String =
    when (effect) {
        is SelectionEffect.CopyMessages -> {
            copyText(copiedText(effect.messages, resources))
            copiedNotice(resources)
        }

        SelectionEffect.Reported -> {
            resources.getString(R.string.report_sent)
        }

        SelectionEffect.RequestStoragePermission -> {
            if (showsStorageRationale) {
                resources.getString(R.string.save_storage_rationale)
            } else {
                requestStoragePermission()
                ""
            }
        }

        SelectionEffect.StoragePermissionDenied -> {
            resources.getString(R.string.save_storage_denied)
        }

        SelectionEffect.Saving -> {
            resources.getString(R.string.save_downloading)
        }

        is SelectionEffect.Saved -> {
            if (effect.count == 1) {
                resources.getString(R.string.save_done)
            } else {
                resources.getQuantityString(R.plurals.save_done_many, effect.count, effect.count)
            }
        }

        SelectionEffect.SaveFailed -> {
            resources.getString(R.string.save_failed)
        }
    }

/** The snackbar after a copy: none from Android 13 on, which confirms copies itself. */
internal fun copiedNotice(resources: Resources): String =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) resources.getString(R.string.message_copied) else ""

/**
 * Several messages as one text, as the official apps copy them: each under "Ada Lovelace, [Oct 19, 9:41 AM]" (its
 * sender and when it was sent, [scheduledTime]'s format), a blank line between them.
 */
internal fun Context.copiedText(
    messages: List<CopiedMessage>,
    resources: Resources,
): String =
    messages.joinToString(separator = "\n\n") { message ->
        val header =
            resources.getString(
                R.string.selection_copy_header,
                message.sender.resolve(resources),
                scheduledTime(message.date),
            )
        "$header\n${message.text}"
    }

/**
 * The snackbar after forwarding ([ChatEffect.Forwarded], [ChatEffect.ForwardFailed]): "Forwarded to Ada" for one
 * chat whose name the sheet showed, else "Forwarded to 3 chats"; or why it failed, with "Not forwarded to 2 chats."
 * first where other chats got the messages. Empty for other effects.
 */
internal fun forwardNotice(
    resources: Resources,
    effect: ChatEffect,
    showErrorDetails: Boolean,
): String =
    when (effect) {
        is ChatEffect.Forwarded -> {
            val title = effect.chatTitle
            if (title != null) {
                resources.getString(R.string.forward_done_to, title.resolve(resources))
            } else {
                resources.getQuantityString(R.plurals.forward_done, effect.count, effect.count)
            }
        }

        is ChatEffect.ForwardFailed -> {
            val reason = errorMessage(resources, effect.error, showErrorDetails)
            if (effect.forwarded == 0) {
                reason
            } else {
                resources.getQuantityString(R.plurals.forward_failed_some, effect.failed, effect.failed, reason)
            }
        }

        else -> {
            ""
        }
    }
