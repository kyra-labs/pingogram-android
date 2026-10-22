package dev.kyralabs.pingogram.feature.chat.ui.inline

/**
 * An inline query typed in the composer's field: "@[username] [query]", the bot's username (without "@") and what
 * follows the space after it, which may be empty ("@gif ": the bot's suggestions). `toString()` leaves the query out.
 */
internal data class TypedInlineQuery(
    val username: String,
    val query: String,
) {
    override fun toString() = "TypedInlineQuery(username=$username, …)"
}

/**
 * The inline query of [text]: where it starts with "@", a username (Latin letters, digits and "_", up to
 * [MAX_USERNAME_LENGTH]) and a space, as Telegram's apps take it, so a mention being typed ("@ad") or one that opens a
 * message ("@ada" then a line) asks no bot; null for any other text. The query is cut at [MAX_QUERY_LENGTH].
 */
internal fun inlineQuery(text: CharSequence): TypedInlineQuery? =
    INLINE_QUERY.matchEntire(text)?.let { match ->
        TypedInlineQuery(username = match.groupValues[1], query = match.groupValues[2].take(MAX_QUERY_LENGTH))
    }

/** Telegram's limit on a username (5–32 characters; some old bots, such as @gif, have fewer). */
private const val MAX_USERNAME_LENGTH = 32

/** The longest query Telegram passes to a bot (the Bot API's `InlineQuery.query`: up to 256 characters). */
private const val MAX_QUERY_LENGTH = 256

/** "@", a username and one space, then the query, which may run over lines. */
private val INLINE_QUERY = Regex("@([A-Za-z0-9_]{1,$MAX_USERNAME_LENGTH}) ([\\s\\S]*)")
