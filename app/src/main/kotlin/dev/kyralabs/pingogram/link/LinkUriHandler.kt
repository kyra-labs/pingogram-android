package dev.kyralabs.pingogram.link

import androidx.compose.ui.platform.UriHandler
import dev.kyralabs.pingogram.core.domain.model.isTelegramLink

/**
 * The app's [UriHandler] (`LocalUriHandler`, provided by `MainActivity`): the one hook every link tapped in the app
 * goes through, in messages, bios, descriptions, link previews and sponsored messages. Telegram links
 * ([isTelegramLink]) go to [openTelegramLink], which opens them in Pingogram; every other link to [platform], the
 * system's handler, which opens it in the app the user chose for it (a browser) and throws [IllegalArgumentException]
 * if there is none.
 */
internal class LinkUriHandler(
    private val platform: UriHandler,
    private val openTelegramLink: (String) -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        if (isTelegramLink(uri)) openTelegramLink(uri) else platform.openUri(uri)
    }
}
