package dev.kyralabs.pingogram.core.domain.model

/**
 * The link to the user's own profile, which their QR code holds (TDLib's `getUserLink`): `https://t.me/<username>`
 * for an account with a username, which lasts; otherwise a temporary link (a contact token) that lasts
 * [expiresInSeconds]. `toString()` leaves the link out, as it opens the account.
 */
data class ProfileLink(
    val url: String,
    val expiresInSeconds: Int,
) {
    /** A link that stops working after a while, as the account has no username. */
    val isTemporary: Boolean get() = expiresInSeconds > 0

    override fun toString() = "ProfileLink(expiresInSeconds=$expiresInSeconds)"
}
