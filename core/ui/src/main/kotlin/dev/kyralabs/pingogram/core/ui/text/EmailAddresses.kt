package dev.kyralabs.pingogram.core.ui.text

/** A loose check before asking Telegram: one `@` with something on both sides, and no spaces. */
fun isPlausibleEmailAddress(address: String): Boolean {
    val at = address.indexOf('@')
    return at > 0 && at == address.lastIndexOf('@') && at < address.lastIndex && address.none { it.isWhitespace() }
}
