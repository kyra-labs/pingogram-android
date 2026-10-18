package dev.kyralabs.pingogram.core.common

import javax.inject.Qualifier

/**
 * Qualifies the app-wide `CoroutineScope`, which outlives every screen: repositories collect TDLib's updates in it
 * (`docs/ARCHITECTURE.md`, Layers and data flow). `:app` provides it, with a `SupervisorJob` on the
 * `@Dispatcher(Default)` dispatcher; tests pass a `TestScope`'s `backgroundScope` instead.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
