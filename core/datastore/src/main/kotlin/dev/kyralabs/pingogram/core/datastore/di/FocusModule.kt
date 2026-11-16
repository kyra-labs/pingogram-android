package dev.kyralabs.pingogram.core.datastore.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.datastore.DataStoreFocusRepository
import dev.kyralabs.pingogram.core.domain.focus.DefaultFocusGate
import dev.kyralabs.pingogram.core.domain.focus.FocusGate
import dev.kyralabs.pingogram.core.domain.focus.FocusToggle
import dev.kyralabs.pingogram.core.domain.repository.FocusRepository
import javax.inject.Singleton

/** The Focus profiles of the settings file ([DataStoreModule]), what they let through, the tile's toggle (board 31). */
@Module
@InstallIn(SingletonComponent::class)
interface FocusModule {
    @Binds
    fun focusRepository(repository: DataStoreFocusRepository): FocusRepository

    companion object {
        /** What Focus lets through, worked out from the clock as it is asked. */
        @Provides
        @Singleton
        fun focusGate(
            repository: FocusRepository,
            clock: WallClock,
        ): FocusGate = DefaultFocusGate(repository, clock)

        /** The Quick Settings tile's one tap: the last used profile on, or Focus off. */
        @Provides
        fun focusToggle(
            repository: FocusRepository,
            clock: WallClock,
        ): FocusToggle = FocusToggle(repository, clock)
    }
}
