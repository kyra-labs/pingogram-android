package dev.kyralabs.pingogram.di

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.serviceLoaderEnabled
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.ui.image.TdlibFileFetcher
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImageKeyer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * The app's Coil [ImageLoader] (`docs/PERFORMANCE.md` §4), which `PingogramApplication` hands to Coil the first time an
 * image is shown, so nothing is built at start-up.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {
    @Provides
    @Singleton
    fun imageLoader(
        @ApplicationContext context: Context,
        fileRepository: FileRepository,
        authRepository: AuthRepository,
        @ApplicationScope scope: CoroutineScope,
    ): ImageLoader =
        tdlibImageLoader(context, fileRepository).also { imageLoader ->
            imageLoader.clearMemoryCacheWhenTdlibCloses(authRepository.authStep, scope)
        }
}

/**
 * Loads [TdlibFileImage]s from TDLib's files ([TdlibFileFetcher]) and keeps decoded images in memory only:
 *
 * - a memory cache of 25% of the app's memory class, keyed by file id ([TdlibFileImageKeyer]);
 * - no disk cache: TDLib keeps the files, and a second copy would only take storage;
 * - hardware bitmaps, which keep pixels in graphics memory, out of the app's heap;
 * - no network: Coil has no network fetcher (no `coil-network-*` artifact, `gradle/libs.versions.toml`), and
 *   components that libraries register through Java's service loader are ignored, so none can add one
 *   (`REQUIREMENTS.md` §6.2). Coil's own decoders and its fetchers for local data stay.
 */
internal fun tdlibImageLoader(
    context: Context,
    fileRepository: FileRepository,
): ImageLoader =
    ImageLoader
        .Builder(context)
        .components {
            add(TdlibFileImageKeyer())
            add(TdlibFileFetcher.Factory(fileRepository))
        }.memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
        .diskCache(null)
        .allowHardware(true)
        .serviceLoaderEnabled(false)
        .build()

/**
 * Empties the memory cache whenever TDLib closes ([AuthStep.Closed], after logout): file ids belong to one TDLib
 * instance, and the next one reuses them for other files. [authSteps] reports `Closed` from TDLib's updates, which,
 * unlike the conflated auth state, never skip it.
 */
internal fun ImageLoader.clearMemoryCacheWhenTdlibCloses(
    authSteps: Flow<AuthStep>,
    scope: CoroutineScope,
) {
    scope.launch {
        authSteps.filterIsInstance<AuthStep.Closed>().collect { memoryCache?.clear() }
    }
}

private const val MEMORY_CACHE_PERCENT = 0.25
