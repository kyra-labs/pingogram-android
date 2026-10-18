package dev.kyralabs.pingogram.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.BuildConfig
import dev.kyralabs.pingogram.core.domain.model.AppInfo

/** The app's own build, from `BuildConfig`, for features that show it (About) without depending on `:app`. */
@Module
@InstallIn(SingletonComponent::class)
object AppInfoModule {
    @Provides
    fun appInfo(): AppInfo = AppInfo(versionName = BuildConfig.VERSION_NAME)
}
