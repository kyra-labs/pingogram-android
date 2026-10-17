package dev.kyralabs.pingogram.buildlogic

import java.io.StringReader
import java.util.Properties

/**
 * Telegram API credentials from the git-ignored `secrets.properties` in the repository root, exposed to
 * `:app` as `BuildConfig.TELEGRAM_API_ID` / `TELEGRAM_API_HASH` (`docs/ARCHITECTURE.md`, Build & modules).
 *
 * There is no fallback: a missing or malformed file fails the build. Errors name the offending key but
 * never echo a value (CLAUDE.md rule 1).
 */
class TelegramSecrets private constructor(
    val apiId: Int,
    val apiHash: String,
) {
    companion object {
        const val FILE_NAME = "secrets.properties"
        const val API_ID = "TELEGRAM_API_ID"
        const val API_HASH = "TELEGRAM_API_HASH"

        private const val WHERE_TO_GET =
            "with the api_id and api_hash from https://my.telegram.org (see docs/OWNER_SETUP.md)"
        private val NON_NEGATIVE_INT = Regex("[0-9]+")
        private val HEX_32 = Regex("[0-9a-fA-F]{32}")

        /** [contents] is the text of `secrets.properties`, or null if the file does not exist. */
        fun parse(contents: String?): TelegramSecrets {
            checkNotNull(contents) {
                "$FILE_NAME not found in the repository root. Copy secrets.properties.example to $FILE_NAME " +
                    "and fill it in $WHERE_TO_GET."
            }
            val properties = Properties().apply { load(StringReader(contents)) }
            val rawId = properties.getProperty(API_ID)?.trim()?.takeIf(String::isNotEmpty)
            val rawHash = properties.getProperty(API_HASH)?.trim()?.takeIf(String::isNotEmpty)
            val apiId = rawId?.takeIf(NON_NEGATIVE_INT::matches)?.toIntOrNull()
            val apiHash = rawHash?.takeIf(HEX_32::matches)

            check(apiId != null && apiHash != null) {
                val problems = listOfNotNull(
                    problem(API_ID, rawId, apiId, "must be a non-negative integer"),
                    problem(API_HASH, rawHash, apiHash, "must be exactly 32 hexadecimal characters"),
                )
                "$FILE_NAME is invalid: ${problems.joinToString("; ")}. " +
                    "Use the format of secrets.properties.example $WHERE_TO_GET."
            }
            return TelegramSecrets(apiId, apiHash)
        }

        private fun problem(key: String, raw: String?, parsed: Any?, requirement: String): String? = when {
            raw == null -> "$key is missing or empty"
            parsed == null -> "$key $requirement"
            else -> null
        }
    }
}
