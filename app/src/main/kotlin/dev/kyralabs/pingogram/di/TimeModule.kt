package dev.kyralabs.pingogram.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.common.time.SystemWallClock
import dev.kyralabs.pingogram.core.common.time.WallClock

/**
 * The [WallClock] binding (`docs/PERFORMANCE.md` §4: one shared minute tick for every ViewModel). Here rather than in
 * `:core:common`, a plain JVM module without Hilt, like [DispatchersModule]; [SystemWallClock] is a singleton, so its
 * timer is shared.
 */
@Module
@InstallIn(SingletonComponent::class)
interface TimeModule {
    @Binds
    fun wallClock(clock: SystemWallClock): WallClock
}
