package dev.kyralabs.pingogram.core.domain.model

/**
 * The connection to Telegram, for the connection pill and offline banners (`REQUIREMENTS.md` FR-L5, §6.5).
 * Requests made while not [Ready] wait until the connection is back.
 */
enum class ConnectionStatus {
    WaitingForNetwork,
    ConnectingToProxy,
    Connecting,

    /** Connected, and catching up on what happened while offline. */
    Updating,
    Ready,
}
