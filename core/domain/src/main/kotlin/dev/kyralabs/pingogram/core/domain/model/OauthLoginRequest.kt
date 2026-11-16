package dev.kyralabs.pingogram.core.domain.model

/**
 * A website's or an app's request to log in with the Telegram account (td_api.tl `oauthLinkInfo`, "Log in with
 * Telegram").
 *
 * @property domain where the user logs in, e.g. `example.org`.
 * @property appName the app's verified name; empty where Telegram verified none ("Unverified app").
 * @property isApp the request comes from an app rather than a website.
 * @property botUserId the bot of the website or app; 0 for none.
 * @property asksToMessage the bot asks to send the user messages.
 * @property asksForPhoneNumber the website or app asks for the user's phone number.
 * @property asksForTonWallet the app asks for the user's TON wallet, which Pingogram never shares.
 * @property browser the browser the request came from, e.g. "Firefox 145"; empty where unknown.
 * @property platform the system that browser runs on; empty where unknown.
 * @property location where the request came from, by its IP address; empty where unknown.
 * @property matchCodes the codes the website shows, one of which the user picks first; empty where none is asked.
 */
data class OauthLoginRequest(
    val domain: String,
    val appName: String,
    val isApp: Boolean,
    val botUserId: Long,
    val asksToMessage: Boolean,
    val asksForPhoneNumber: Boolean,
    val asksForTonWallet: Boolean,
    val browser: String,
    val platform: String,
    val location: String,
    val matchCodes: List<String>,
) {
    /** The user picks the code the website shows before anything else. */
    val needsMatchCode: Boolean
        get() = matchCodes.isNotEmpty()

    override fun toString() = "OauthLoginRequest(isApp=$isApp, asksToMessage=$asksToMessage, …)"
}
