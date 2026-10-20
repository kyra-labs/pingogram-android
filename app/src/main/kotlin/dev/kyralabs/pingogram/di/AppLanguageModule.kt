package dev.kyralabs.pingogram.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import dev.kyralabs.pingogram.language.PlatformAppLanguageRepository

/**
 * The [AppLanguageRepository] binding. In `:app`, which declares the languages it ships (`res/xml/locales_config.xml`)
 * and applies the language to its activity before Android 13, so the Language page of `:feature:settings` reads it
 * without depending on `:app`.
 */
@Module
@InstallIn(SingletonComponent::class)
interface AppLanguageModule {
    @Binds
    fun appLanguageRepository(repository: PlatformAppLanguageRepository): AppLanguageRepository
}
