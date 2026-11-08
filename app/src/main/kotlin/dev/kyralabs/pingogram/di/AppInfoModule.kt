package dev.kyralabs.pingogram.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.BuildConfig
import dev.kyralabs.pingogram.core.domain.model.AppInfo
import javax.inject.Singleton

/**
 * The app's own build, from `BuildConfig`, for features that show it (About, the feedback report) without depending on
 * `:app`. The version code is the base one: per-ABI APKs add their ABI's digit on top (`docs/RELEASE.md`). The
 * installer is read once per process: an update through another source starts a new process.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppInfoModule {
    @Provides
    @Singleton
    fun appInfo(
        @ApplicationContext context: Context,
    ): AppInfo =
        AppInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            flavor = BuildConfig.FLAVOR,
            isDebug = BuildConfig.DEBUG,
            installer = installerOf(context),
        )
}

/** The package that installed this app, as Android records it; null when it names none or cannot say. */
private fun installerOf(context: Context): String? =
    try {
        val packageManager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION") // The only way before Android 11, where getInstallSourceInfo is missing.
            packageManager.getInstallerPackageName(context.packageName)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
