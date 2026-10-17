package dev.kyralabs.pingogram.core.tdlib

import org.drinkless.tdlib.TdApi

/**
 * Mirror of TDLib's `ConnectionState`, for the connection pill in the chat list app bar (`REQUIREMENTS.md` FR-L5).
 * TDLib reports changes only, so [TdlibClient] starts at [Connecting].
 */
enum class ConnectionState {
    WaitingForNetwork,
    ConnectingToProxy,
    Connecting,
    Updating,
    Ready,
    ;

    companion object {
        /** Null only for a state this TDLib version does not define. */
        fun from(state: TdApi.ConnectionState): ConnectionState? =
            when (state) {
                is TdApi.ConnectionStateWaitingForNetwork -> WaitingForNetwork
                is TdApi.ConnectionStateConnectingToProxy -> ConnectingToProxy
                is TdApi.ConnectionStateConnecting -> Connecting
                is TdApi.ConnectionStateUpdating -> Updating
                is TdApi.ConnectionStateReady -> Ready
                else -> null
            }
    }
}
