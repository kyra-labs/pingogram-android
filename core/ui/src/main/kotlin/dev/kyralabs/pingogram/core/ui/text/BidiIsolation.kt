package dev.kyralabs.pingogram.core.ui.text

/** FIRST STRONG ISOLATE: what follows, up to [POP_DIRECTIONAL_ISOLATE], takes the direction of its first letter. */
internal const val FIRST_STRONG_ISOLATE = '⁨'

/** POP DIRECTIONAL ISOLATE: ends the text [FIRST_STRONG_ISOLATE] began. */
internal const val POP_DIRECTIONAL_ISOLATE = '⁩'

/**
 * [this] text from people or from Telegram (a name, a title, a caption, a phone number) as an argument of one of
 * Pingogram's own strings: "Grace is typing…", "Forwarded from …", "Alice joined the group" (`docs/DESIGN_SYSTEM.md`
 * §9). Wrapped in Unicode first-strong isolates, it takes its own direction without changing the string's around it:
 * a Hebrew name in an English string, or an English name in an Arabic one, no longer pulls the words and punctuation
 * next to it into its direction, and a phone number stays left to right in a right-to-left string. Isolates are
 * invisible and screen readers skip them.
 *
 * The string around it then takes its own language's direction rather than the layout's, or English strings in a
 * right-to-left layout (Pingogram ships only English so far) would set the isolated name apart in the wrong order:
 * shown with [userContent], whose first letter outside the isolates is the string's own, or, where it is formatted
 * for a text that keeps the layout's direction (a snackbar, a dialog, a sign-in step), isolated as a whole too.
 *
 * Text shown on its own is never isolated: an isolate around all of it hides its first letter from [userContent].
 * Wrapped once, where the string is built (a mapper or a ViewModel where there is one, `docs/PERFORMANCE.md` §4);
 * empty text stays empty.
 */
fun String.bidiIsolated(): String = if (isEmpty()) this else "$FIRST_STRONG_ISOLATE$this$POP_DIRECTIONAL_ISOLATE"

/**
 * [bidiIsolated] for a text as data: a [UiText.Plain] (a name, a title) is isolated; Pingogram's own strings
 * ("Someone", "Deleted Account") are in the app's language already and stay as they are.
 */
fun UiText.bidiIsolated(): UiText = if (this is UiText.Plain) UiText.Plain(text.bidiIsolated()) else this
