package dev.kyralabs.pingogram.core.domain.model

/**
 * Whether [url] is a Telegram link that Pingogram opens itself, through `LinkRepository.resolve`, rather than a
 * browser: `tg:` links, and `http(s)` links to `t.me`, `telegram.me` or `telegram.dog` or one of their subdomains
 * (`username.t.me`), in any case. TDLib decides what such a link is; links to Telegram's other sites
 * (`telegram.org`, `telegra.ph`) open in the browser. Only the scheme and the host are looked at, never parsed by
 * `java.net.URI`, which refuses links people write (spaces, unencoded characters) that TDLib accepts.
 */
fun isTelegramLink(url: String): Boolean {
    val link = url.trim()
    val scheme = link.substringBefore(':', missingDelimiterValue = "").lowercase()
    val isWeb = (scheme == "http" || scheme == "https") && link.startsWith("$scheme://", ignoreCase = true)
    return scheme == TG_SCHEME || (isWeb && webHostOf(link).isTelegramHost())
}

/** The host of web link [link] (`scheme://user@host:port/…`), lowercase, without a trailing dot. */
private fun webHostOf(link: String): String {
    val authority = link.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' && it != '\\' }
    return authority
        .substringAfterLast('@')
        .substringBefore(':')
        .lowercase()
        .removeSuffix(".")
}

/** Whether this host is one of [TELEGRAM_LINK_HOSTS] or a subdomain of one. */
private fun String.isTelegramHost(): Boolean = TELEGRAM_LINK_HOSTS.any { this == it || endsWith(".$it") }

/** Whether [url] is a web link (`http`, `https`), which a browser can open. */
fun isWebLink(url: String): Boolean {
    val scheme = url.trim().substringBefore(':', missingDelimiterValue = "").lowercase()
    return scheme == "http" || scheme == "https"
}

/** The scheme of Telegram's app links, `tg://resolve?domain=…`. */
const val TG_SCHEME = "tg"

/** The hosts of Telegram's web links, which TDLib's `getInternalLinkType` reads (`docs/adr/0013-deep-links.md`). */
val TELEGRAM_LINK_HOSTS = listOf("t.me", "telegram.me", "telegram.dog")
