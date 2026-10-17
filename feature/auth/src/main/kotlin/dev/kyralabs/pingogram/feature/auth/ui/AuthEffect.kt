package dev.kyralabs.pingogram.feature.auth.ui

import dev.kyralabs.pingogram.core.common.result.DomainError

/** One-off side effects of [AuthViewModel], handled once by [AuthRoute]. */
sealed interface AuthEffect {
    /** Opens [url] in the browser (terms of service, privacy policy, fragment.com). */
    data class OpenUrl(
        val url: String,
    ) : AuthEffect

    /** Opens the user's email app with a new message. */
    data class SendEmail(
        val address: String,
        val subject: String,
    ) : AuthEffect

    /** A failure that concerns no single field: shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : AuthEffect

    /** The account is logged in: leave the login flow. */
    data object Authorized : AuthEffect
}
