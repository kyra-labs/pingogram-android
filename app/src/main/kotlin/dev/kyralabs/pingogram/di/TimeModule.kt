package dev.kyralabs.pingogram.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.common.time.SystemWallClock
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.ui.time.AndroidTimePatterns
import dev.kyralabs.pingogram.core.ui.time.TimePatterns

/**
 * The [WallClock] binding (`docs/PERFORMANCE.md` §4: one shared minute tick for every ViewModel). Here rather than in
 * `:core:common`, a plain JVM module without Hilt, like [DispatchersModule]; [SystemWallClock] is a singleton, so its
 * timer is shared. The platform's [TimePatterns] too, which `:core:ui` (without Hilt) declares for the ViewModels of
 * several features.
 */
@Module
@InstallIn(SingletonComponent::class)
interface TimeModule {
    @Binds
    fun wallClock(clock: SystemWallClock): WallClock

    companion object {
        @Provides
        fun timePatterns(
            @ApplicationContext context: Context,
        ): TimePatterns = AndroidTimePatterns(context)
    }
}
