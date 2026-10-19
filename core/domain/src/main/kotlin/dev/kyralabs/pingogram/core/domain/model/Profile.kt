package dev.kyralabs.pingogram.core.domain.model

/**
 * What the logged-in user edits of their own profile (`REQUIREMENTS.md` FR-S1, Account), as TDLib has it: names,
 * username and bio. The photo is the user's `profilePhoto` (`UserRepository`).
 *
 * `toString()` leaves everything out, so a profile can be logged.
 *
 * @property lastName empty without one.
 * @property username the username the user can change, without `@`; empty without one. Collectible usernames
 *   (bought on Fragment) are not it.
 * @property bio the bio as plain text; empty without one.
 */
data class EditableProfile(
    val firstName: String,
    val lastName: String,
    val username: String,
    val bio: String,
) {
    override fun toString() = "EditableProfile(…)"
}

/** What Telegram says about a username the user wants (td_api.tl `CheckChatUsernameResult`). */
enum class UsernameCheck {
    /** Free to take. */
    Available,

    /** Telegram does not accept it as a username. */
    Invalid,

    /** Someone else has it. */
    Taken,

    /** It is for sale on Fragment and can only be bought there. */
    ForSale,

    /** It cannot be taken for another reason, e.g. too many public links. */
    Unavailable,
}

/** The limits Telegram sets on a profile, in Unicode characters (code points), as TDLib counts them. */
object ProfileLimits {
    /** The longest first or last name; TDLib cuts longer ones (`clean_name`, td/telegram/misc.cpp). */
    const val NAME_LENGTH_MAX = 64

    const val USERNAME_LENGTH_MIN = 5
    const val USERNAME_LENGTH_MAX = 32

    /** The longest bio until TDLib reports its option `bio_length_max` (larger with Telegram Premium). */
    const val BIO_LENGTH_MAX_DEFAULT = 70
}

/** Why a username cannot be taken, found without asking Telegram ([usernameProblem]). */
enum class UsernameProblem {
    TooShort,
    TooLong,

    /** It has a character other than a–z, A–Z, 0–9 and `_`. */
    InvalidCharacter,

    /** It starts with a digit or an underscore. */
    StartsWithNonLetter,
    EndsWithUnderscore,
    ConsecutiveUnderscores,
}

/**
 * Why Telegram would not accept [username] as a username, as TDLib checks it (`is_allowed_username`,
 * td/telegram/misc.cpp) and Telegram's minimum length; null if it could be one. The first problem found, in the order
 * a user typing it meets them: characters, the first letter, the length, then underscores.
 */
fun usernameProblem(username: String): UsernameProblem? =
    when {
        username.any { !it.isUsernameCharacter() } -> UsernameProblem.InvalidCharacter
        username.isNotEmpty() && !username.first().isAsciiLetter() -> UsernameProblem.StartsWithNonLetter
        username.length < ProfileLimits.USERNAME_LENGTH_MIN -> UsernameProblem.TooShort
        username.length > ProfileLimits.USERNAME_LENGTH_MAX -> UsernameProblem.TooLong
        username.endsWith('_') -> UsernameProblem.EndsWithUnderscore
        "__" in username -> UsernameProblem.ConsecutiveUnderscores
        else -> null
    }

/**
 * How many characters TDLib counts in this text: Unicode code points, so a character outside the Basic Multilingual
 * Plane (most emoji) counts once, not as its two UTF-16 units.
 */
fun String.characterCount(): Int = codePointCount(0, length)

private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isUsernameCharacter() = isAsciiLetter() || this in '0'..'9' || this == '_'
