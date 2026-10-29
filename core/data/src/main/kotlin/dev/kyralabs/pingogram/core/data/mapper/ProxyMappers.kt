package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.ProxyType
import dev.kyralabs.pingogram.core.domain.model.SavedProxy
import org.drinkless.tdlib.TdApi

/** The proxy TDLib describes, or null for a protocol of a newer TDLib that Pingogram does not know. */
internal fun TdApi.Proxy.toProxy(): Proxy? {
    val type =
        when (val type = type) {
            is TdApi.ProxyTypeSocks5 -> ProxyType.Socks5(type.username.orEmpty(), type.password.orEmpty())
            is TdApi.ProxyTypeHttp -> ProxyType.Http(type.username.orEmpty(), type.password.orEmpty(), type.httpOnly)
            is TdApi.ProxyTypeMtproto -> ProxyType.Mtproto(type.secret.orEmpty())
            else -> return null
        }
    return Proxy(server = server.orEmpty(), port = port, type = type)
}

internal fun TdApi.AddedProxy.toSavedProxy(): SavedProxy? =
    proxy?.toProxy()?.let { proxy ->
        SavedProxy(
            id = id,
            proxy = proxy,
            isEnabled = isEnabled,
            lastUsedDate = lastUsedDate.toLong(),
            name = comment.orEmpty(),
        )
    }

internal fun Proxy.toTdlib(): TdApi.Proxy =
    TdApi.Proxy(
        server,
        port,
        when (val type = type) {
            is ProxyType.Socks5 -> TdApi.ProxyTypeSocks5(type.username, type.password)
            is ProxyType.Http -> TdApi.ProxyTypeHttp(type.username, type.password, type.httpOnly)
            is ProxyType.Mtproto -> TdApi.ProxyTypeMtproto(type.secret)
        },
    )
