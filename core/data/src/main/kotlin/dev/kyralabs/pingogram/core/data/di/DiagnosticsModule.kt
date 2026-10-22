package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.data.diagnostics.AndroidDiagnosticsRepository
import dev.kyralabs.pingogram.core.domain.repository.DiagnosticsRepository

/** The feedback report's data (`docs/TESTING_GUIDE.md`); the crash recorder injects itself. */
@Module
@InstallIn(SingletonComponent::class)
interface DiagnosticsModule {
    @Binds
    fun diagnosticsRepository(repository: AndroidDiagnosticsRepository): DiagnosticsRepository
}
