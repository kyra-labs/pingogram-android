package dev.kyralabs.pingogram.feature.scanner

/** What the QR code scanner is opened for, which decides what it accepts and what it says. */
enum class ScanPurpose {
    /** Any Telegram link: a username, a profile, an invitation, a login of another device. */
    Any,

    /**
     * Linking a desktop device (Settings → Active sessions): only the QR code another Telegram app shows to log in
     * (`tg://login?token=…`); others are explained, not opened.
     */
    DeviceLogin,
}
