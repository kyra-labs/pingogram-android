package dev.kyralabs.pingogram.core.domain.model

/**
 * A proxy server Pingogram can reach Telegram through (`docs/adr/0027-proxies.md`): TDLib connects to [server] on
 * [port] and speaks [type]'s protocol there. Only the user adds one, typed in or from a link they confirmed.
 *
 * `toString()` leaves out the address and the credentials, so a proxy can be logged (`REQUIREMENTS.md` §6.2).
 */
data class Proxy(
    val server: String,
    val port: Int,
    val type: ProxyType,
) {
    override fun toString() = "Proxy(type=${type.kind}, …)"
}

/** The protocol of a [Proxy], with what it needs to log in. */
sealed interface ProxyType {
    /** Which of the three protocols this is. */
    val kind: ProxyKind

    /** A SOCKS5 proxy; [username] and [password] empty when it needs none. */
    data class Socks5(
        val username: String = "",
        val password: String = "",
    ) : ProxyType {
        override val kind get() = ProxyKind.Socks5

        override fun toString() = "Socks5(…)"
    }

    /**
     * An HTTP proxy; [username] and [password] empty when it needs none. [httpOnly]: it takes only HTTP requests, not
     * the tunnels of HTTP CONNECT, so TDLib sends Telegram's traffic as HTTP requests through it.
     */
    data class Http(
        val username: String = "",
        val password: String = "",
        val httpOnly: Boolean = false,
    ) : ProxyType {
        override val kind get() = ProxyKind.Http

        override fun toString() = "Http(httpOnly=$httpOnly, …)"
    }

    /** One of Telegram's MTProto proxies, with its [secret] (hexadecimal or base64url, as its link gives it). */
    data class Mtproto(
        val secret: String,
    ) : ProxyType {
        override val kind get() = ProxyKind.Mtproto

        override fun toString() = "Mtproto(…)"
    }
}

/** The protocols a [Proxy] can speak, in the order Pingogram offers them. */
enum class ProxyKind {
    Socks5,
    Http,
    Mtproto,
}

/**
 * A proxy the user added, as TDLib keeps it (`getProxies`).
 *
 * @property id TDLib's id of it, to change, use or remove it.
 * @property isEnabled TDLib connects through it now; at most one proxy is.
 * @property lastUsedDate when TDLib last connected through it, in Unix seconds; 0 if never.
 * @property name the user's name for it; empty for none.
 */
data class SavedProxy(
    val id: Int,
    val proxy: Proxy,
    val isEnabled: Boolean,
    val lastUsedDate: Long,
    val name: String,
) {
    override fun toString() = "SavedProxy(id=$id, proxy=$proxy, isEnabled=$isEnabled, lastUsedDate=$lastUsedDate)"
}
