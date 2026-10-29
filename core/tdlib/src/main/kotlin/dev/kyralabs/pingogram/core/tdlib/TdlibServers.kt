package dev.kyralabs.pingogram.core.tdlib

/**
 * The Telegram servers a TDLib instance talks to (`SetTdlibParameters.use_test_dc`): Telegram's own, or its test
 * servers, whose test accounts (+99966 X YYYY, https://core.telegram.org/api/auth#test-accounts) sign in for Google
 * Play's review (`REQUIREMENTS.md` FR-A9). Each keeps a database and files of its own ([TdlibDirectories]).
 */
enum class TdlibServers {
    Production,
    Test,
}
