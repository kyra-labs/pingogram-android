package dev.kyralabs.pingogram.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.BuildConfig
import dev.kyralabs.pingogram.core.domain.model.AppInfo

/**
 * The app's own build, from `BuildConfig`, for features that show it (About, the feedback report) without depending on
 * `:app`. The version code is the base one: per-ABI APKs add their ABI's digit on top (`docs/RELEASE.md`).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppInfoModule {
    @Provides
    fun appInfo(): AppInfo =
        AppInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            flavor = BuildConfig.FLAVOR,
            isDebug = BuildConfig.DEBUG,
        )
}
