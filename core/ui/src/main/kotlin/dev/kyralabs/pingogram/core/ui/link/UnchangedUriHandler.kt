package dev.kyralabs.pingogram.core.ui.link

import androidx.compose.ui.platform.UriHandler

/**
 * A [UriHandler] that may change a link before it opens (the app's, which removes tracking parameters while Privacy
 * and security says so, `REQUIREMENTS.md` FR-X8) and can also open one exactly as given ([openUriUnchanged]): a
 * sponsored message's link, which Pingogram never changes (`CLAUDE.md`, Telegram's API terms).
 */
interface UnchangedUriHandler : UriHandler {
    /** Opens [uri] as it is, as [openUri] would otherwise; throws [IllegalArgumentException] where no app opens it. */
    fun openUriUnchanged(uri: String)
}

/** Opens [uri] exactly as given: through [UnchangedUriHandler.openUriUnchanged] where this handler can change links. */
fun UriHandler.openUnchanged(uri: String) {
    if (this is UnchangedUriHandler) openUriUnchanged(uri) else openUri(uri)
}
