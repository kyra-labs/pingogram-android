package dev.kyralabs.pingogram.core.domain.model

/**
 * Which of Telegram's servers the app uses (`REQUIREMENTS.md` FR-A9). [Test] holds Telegram's test accounts
 * (https://core.telegram.org/api/auth#test-accounts), which Google Play's review signs in with
 * (`docs/PLAY_STORE.md` §1). Every other number signs in on [Production].
 */
enum class TelegramServers {
    Production,
    Test,
}

/** Digits of a test account's number: 99966, the data center (1 to 3), then four digits. */
private val TestPhoneNumber = Regex("99966[1-3][0-9]{4}")

/**
 * The servers [phoneNumber] signs in on: [TelegramServers.Test] only for a test account's number (+99966 X YYYY,
 * whatever separators were typed), [TelegramServers.Production] for every other.
 */
fun telegramServersFor(phoneNumber: String): TelegramServers {
    val digits = phoneNumber.filter(Char::isDigit)
    val onlyDigits = phoneNumber.all { it.isDigit() || it in PHONE_NUMBER_SEPARATORS }
    return if (onlyDigits && isTestPhoneNumber(digits)) TelegramServers.Test else TelegramServers.Production
}

/** Whether [digits] (a number without `+`) are a test account's; see [telegramServersFor]. */
fun isTestPhoneNumber(digits: String): Boolean = TestPhoneNumber.matches(digits)

private const val PHONE_NUMBER_SEPARATORS = "+ -()"
