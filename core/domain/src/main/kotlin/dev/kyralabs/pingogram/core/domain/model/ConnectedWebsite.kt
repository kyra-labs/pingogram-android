package dev.kyralabs.pingogram.core.domain.model

/**
 * A website the user logged in to with their Telegram account ("Log in with Telegram", td_api.tl `connectedWebsite`):
 * its [domain], the bot that logged them in ([botUserId]), the [browser] and [platform] they used, when they logged in
 * and were last active (Unix seconds), and the [ipAddress] and [location] Telegram saw.
 */
data class ConnectedWebsite(
    val id: Long,
    val domain: String,
    val botUserId: Long,
    val browser: String,
    val platform: String,
    val loggedInAt: Long,
    val lastActiveAt: Long,
    val ipAddress: String,
    val location: String,
)
