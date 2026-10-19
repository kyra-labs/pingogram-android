package dev.kyralabs.pingogram.core.domain.model

/**
 * What the user answered the chat list's request to allow notifications (Android 13+, `REQUIREMENTS.md` FR-N1), kept on
 * this device so the request never nags: [isDismissed] once they chose "Not now", [wasRequested] once the system's
 * permission dialog was shown, after which a refusal means Android shows it no more.
 */
data class NotificationPrompt(
    val isDismissed: Boolean = false,
    val wasRequested: Boolean = false,
)
