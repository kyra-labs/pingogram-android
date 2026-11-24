package dev.kyralabs.pingogram.link

import androidx.compose.ui.platform.UriHandler
import dev.kyralabs.pingogram.core.domain.link.LinkCleaner
import dev.kyralabs.pingogram.core.domain.model.isTelegramLink
import dev.kyralabs.pingogram.core.ui.link.UnchangedUriHandler

/**
 * The app's [UriHandler] (`LocalUriHandler`, provided by `MainActivity`): the one hook every link tapped in the app
 * goes through, in messages, bios, descriptions, link previews and sponsored messages. Telegram links
 * ([isTelegramLink]) go to [openTelegramLink], which opens them in Pingogram; every other link to [platform], the
 * system's handler, which opens it in the app the user chose for it (a browser) and throws [IllegalArgumentException]
 * if there is none. While [cleansLinks] says so (Privacy and security, `REQUIREMENTS.md` FR-X8), a link loses its
 * tracking parameters first ([LinkCleaner]); a sponsored message's opens unchanged ([openUriUnchanged]).
 */
internal class LinkUriHandler(
    private val platform: UriHandler,
    private val cleansLinks: () -> Boolean = { false },
    private val openTelegramLink: (String) -> Unit,
) : UnchangedUriHandler {
    override fun openUri(uri: String) = openUriUnchanged(if (cleansLinks()) LinkCleaner.cleanUrl(uri) else uri)

    override fun openUriUnchanged(uri: String) {
        if (isTelegramLink(uri)) openTelegramLink(uri) else platform.openUri(uri)
    }
}
