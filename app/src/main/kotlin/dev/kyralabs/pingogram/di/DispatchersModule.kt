package dev.kyralabs.pingogram.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The `@Dispatcher` bindings (`docs/ARCHITECTURE.md`, Threading). Here rather than next to the qualifier in
 * `:core:common`, a plain JVM module without Hilt's Gradle plugin and KSP; `:app` assembles the DI graph anyway.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    // This is where dispatchers get injected from.
    @Suppress("InjectDispatcher")
    @Provides
    @Dispatcher(Default)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    // This is where dispatchers get injected from.
    @Suppress("InjectDispatcher")
    @Provides
    @Dispatcher(IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
