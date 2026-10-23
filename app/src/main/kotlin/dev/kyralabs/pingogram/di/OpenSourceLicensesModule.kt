package dev.kyralabs.pingogram.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.domain.repository.OpenSourceLicensesRepository
import dev.kyralabs.pingogram.licenses.GeneratedOpenSourceLicensesRepository

/**
 * The [OpenSourceLicensesRepository] binding. In `:app`, which the list of libraries is generated for (only the app's
 * build knows every dependency it ships), so the open-source licenses screen of `:feature:settings` reads it without
 * depending on `:app`, as About reads `AppInfo`.
 */
@Module
@InstallIn(SingletonComponent::class)
interface OpenSourceLicensesModule {
    @Binds
    fun openSourceLicensesRepository(repository: GeneratedOpenSourceLicensesRepository): OpenSourceLicensesRepository
}
