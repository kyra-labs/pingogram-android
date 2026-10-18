package dev.kyralabs.pingogram.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.BuildConfig
import dev.kyralabs.pingogram.core.tdlib.TdlibConfig

/** TDLib settings from `BuildConfig`; the API credentials come from the git-ignored `secrets.properties`. */
@Module
@InstallIn(SingletonComponent::class)
object TdlibConfigModule {
    @Provides
    fun tdlibConfig(): TdlibConfig =
        TdlibConfig(
            apiId = BuildConfig.TELEGRAM_API_ID,
            apiHash = BuildConfig.TELEGRAM_API_HASH,
            appVersionName = BuildConfig.VERSION_NAME,
            isDebug = BuildConfig.DEBUG,
            // The debug-only test DC toggle (REQUIREMENTS.md FR-A9, a "Should") is not built yet: it needs the choice
            // persisted and TDLib restarted with it, while TdlibConfig is fixed for the life of the process.
            useTestDc = false,
        )
}
