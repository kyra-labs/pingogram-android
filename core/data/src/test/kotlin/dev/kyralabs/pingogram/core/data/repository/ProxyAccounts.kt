package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.repository.AccountGraph
import dev.kyralabs.pingogram.core.domain.repository.AccountRepositories
import dev.kyralabs.pingogram.core.testing.FakeProxyRepository
import java.lang.reflect.Proxy as ReflectProxy

/** An account in [slot] whose repositories answer only its [proxies], for the proxy mirror's tests. */
internal fun proxyAccount(
    slot: Int,
    proxies: FakeProxyRepository,
) = AccountGraph(
    slot,
    ReflectProxy.newProxyInstance(
        AccountRepositories::class.java.classLoader,
        arrayOf(AccountRepositories::class.java),
    ) { self, method, args ->
        when (method.name) {
            "proxyRepository" -> proxies
            "equals" -> self === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(self)
            "toString" -> "AccountRepositories($slot)"
            else -> error("Not used: ${method.name}")
        }
    } as AccountRepositories,
)
