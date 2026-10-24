package dev.kyralabs.pingogram.core.domain.model

/**
 * Where a double tap on a message adds the quick reaction (`docs/audit/feature-gaps-octogram.md`, P1): on others'
 * messages ([incoming]) and on the user's own ([outgoing]). Both on by default, as in Telegram's apps; kept on this
 * device. The reaction itself is the account's quick reaction (`QuickReactionRepository`).
 */
data class DoubleTapReactions(
    val incoming: Boolean = true,
    val outgoing: Boolean = true,
)
