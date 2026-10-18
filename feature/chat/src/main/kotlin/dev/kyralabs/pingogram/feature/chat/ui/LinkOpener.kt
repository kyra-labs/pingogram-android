package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener

/**
 * Opens a link of a message with the platform's handler. A link no app opens (a `tg://` link on a device without an
 * app for it) does nothing; Pingogram resolves Telegram's own links itself later (M4).
 */
@Composable
internal fun rememberLinkOpener(): LinkInteractionListener {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            try {
                uriHandler.openUri(url)
            } catch (_: IllegalArgumentException) {
                // No app on the device opens it.
            }
        }
    }
}
