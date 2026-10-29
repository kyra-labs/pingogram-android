package dev.kyralabs.pingogram.share

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * What another app shared with Pingogram (`REQUIREMENTS.md` FR-N2, direct share): [content], and the chat [chatId] of
 * the Direct Share target the user picked in the system's share sheet; null when they picked Pingogram itself, which
 * opens the share sheet to pick chats.
 */
data class IncomingShare(
    val content: SharedContent,
    val chatId: Long? = null,
)

/**
 * The share [intent] carries (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`, the manifest's share filters): its text
 * (`EXTRA_TEXT`) and its files (`EXTRA_STREAM`, or the clip data's URIs), sent as photos and videos when the sharing
 * app's type says they all are ([SharedContent.kindOf]), and the chat of the Direct Share target it went to
 * (`EXTRA_SHORTCUT_ID`, [NotificationIntents.chatOfShortcut]). Null for other intents, for a share with nothing in
 * it, and for one the system hands over again as the app is reopened from the recent apps
 * ([Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY]).
 *
 * Files are `content://` URIs only, read later with the grant they came with; never `file://` ones, and never those
 * of a provider of Pingogram itself ([isOwnContent]), which would let another app make Pingogram send its own files
 * (TDLib's, say) without the user knowing where they came from. At most [SharedContent.MAX_FILES].
 */
internal fun shareOf(
    intent: Intent?,
    isOwnContent: (Uri) -> Boolean,
): IncomingShare? {
    val share =
        intent
            ?.takeIf { it.action == Intent.ACTION_SEND || it.action == Intent.ACTION_SEND_MULTIPLE }
            ?.takeIf { it.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0 }
            ?: return null
    val text =
        share
            .getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
    val files =
        streamsOf(share, multiple = share.action == Intent.ACTION_SEND_MULTIPLE)
            .filter { it.scheme == ContentResolver.SCHEME_CONTENT && !isOwnContent(it) }
            .map(Uri::toString)
            .distinct()
            .take(SharedContent.MAX_FILES)
    val content =
        SharedContent(
            text = if (text.isEmpty()) FormattedText.Empty else FormattedText(text, persistentListOf()),
            files = files.toImmutableList(),
            kind = SharedContent.kindOf(share.type),
        )
    val chatId =
        NotificationIntents.chatOfShortcut(share.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID))?.chatId
    return IncomingShare(content, chatId).takeUnless { content.isEmpty }
}

/** The URIs of the shared files: `EXTRA_STREAM`, or else the clip data's, as some apps send only those. */
private fun streamsOf(
    intent: Intent,
    multiple: Boolean,
): List<Uri> {
    val streams =
        if (multiple) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
    return streams.filterNotNull().ifEmpty {
        intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
    }
}

/**
 * Whether [uri] is served by a content provider of this app (its `FileProvider`, which serves TDLib's files): such a
 * URI never comes from another app's share legitimately.
 */
internal fun Context.isOwnContent(uri: Uri): Boolean {
    val authority = uri.authority ?: return false
    return packageManager.resolveContentProvider(authority, 0)?.packageName == packageName
}
