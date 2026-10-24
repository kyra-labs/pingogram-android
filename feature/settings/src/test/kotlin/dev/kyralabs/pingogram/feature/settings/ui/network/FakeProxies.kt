package dev.kyralabs.pingogram.feature.settings.ui.network

import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import org.drinkless.tdlib.TdApi

/**
 * TDLib's proxies as [client] answers them: [saved] is what `getProxies` returns, the changes apply to it, and pings
 * answer with [pingSeconds] for a server, or fail for the servers in [unreachable].
 */
internal class FakeProxies(
    private val client: FakeTdlibClient,
) {
    var saved = mutableListOf<TdApi.AddedProxy>()
    var pingSeconds = 0.12
    val unreachable = mutableSetOf<String>()
    var refuse: TdError? = null
    private var nextId = 1

    init {
        client.on(TdApi.GetProxies::class) { TdResult.Success(TdApi.AddedProxies(saved.toTypedArray())) }
        client.on(TdApi.AddProxy::class) { request ->
            refuse?.let { return@on TdResult.Failure(it) }
            if (request.enable) disableAll()
            val added = TdApi.AddedProxy(nextId++, 0, request.enable, request.comment, request.proxy)
            saved += added
            TdResult.Success(added)
        }
        client.on(TdApi.EditProxy::class) { request ->
            refuse?.let { return@on TdResult.Failure(it) }
            if (request.enable) disableAll()
            val edited = TdApi.AddedProxy(request.proxyId, 0, request.enable, request.comment, request.proxy)
            saved.replaceAll { if (it.id == request.proxyId) edited else it }
            TdResult.Success(edited)
        }
        client.on(TdApi.EnableProxy::class) { request ->
            disableAll()
            saved.replaceAll { if (it.id == request.proxyId) it.copy(isEnabled = true) else it }
            TdResult.Success(TdApi.Ok())
        }
        client.on(TdApi.DisableProxy::class) {
            disableAll()
            TdResult.Success(TdApi.Ok())
        }
        client.on(TdApi.RemoveProxy::class) { request ->
            saved.removeAll { it.id == request.proxyId }
            TdResult.Success(TdApi.Ok())
        }
        client.on(TdApi.PingProxy::class) { request ->
            if (request.proxy.server in unreachable) {
                TdResult.Failure(TdError(400, "Connection refused"))
            } else {
                TdResult.Success(TdApi.Seconds(pingSeconds))
            }
        }
    }

    fun add(
        server: String,
        port: Int = 1080,
        type: TdApi.ProxyType = TdApi.ProxyTypeSocks5("", ""),
        enabled: Boolean = false,
        lastUsedDate: Int = 0,
        name: String = "",
    ): Int {
        val id = nextId++
        saved += TdApi.AddedProxy(id, lastUsedDate, enabled, name, TdApi.Proxy(server, port, type))
        return id
    }

    private fun disableAll() = saved.replaceAll { it.copy(isEnabled = false) }

    private fun TdApi.AddedProxy.copy(isEnabled: Boolean) =
        TdApi.AddedProxy(id, lastUsedDate, isEnabled, comment, proxy)
}
