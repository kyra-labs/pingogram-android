package dev.kyralabs.pingogram.core.common

import javax.inject.Qualifier

/**
 * Qualifies an injected `CoroutineDispatcher`, e.g. `@Dispatcher(IO) io: CoroutineDispatcher`, so tests can pass
 * a `StandardTestDispatcher` instead (`docs/ARCHITECTURE.md`, Threading). `:app` provides the bindings.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(
    val dispatcher: PingogramDispatchers,
)

enum class PingogramDispatchers {
    /** CPU work: TDLib update handling, mapping, sorting. */
    Default,

    /** Blocking IO: files, the Android Keystore. */
    IO,
}
