package dev.kyralabs.pingogram.core.domain.model

/**
 * What a double tap on a message does (`docs/audit/feature-gaps-octogram.md`, P1; `REQUIREMENTS.md` FR-X15): on
 * others' messages ([incoming]) and on the user's own ([outgoing]), each its own choice. Both add the quick reaction by
 * default, as in Telegram's apps; kept on this device. The reaction itself is the account's quick reaction
 * (`QuickReactionRepository`).
 */
data class DoubleTapReactions(
    val incoming: DoubleTapAction = DoubleTapAction.React,
    val outgoing: DoubleTapAction = DoubleTapAction.React,
)

/** A double tap's action: add the quick reaction ([React]), reply to the message ([Reply]), or [Nothing]. */
enum class DoubleTapAction {
    React,
    Reply,
    Nothing,
}
