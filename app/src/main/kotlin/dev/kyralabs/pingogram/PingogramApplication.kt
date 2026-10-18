package dev.kyralabs.pingogram

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import javax.inject.Inject

@HiltAndroidApp
class PingogramApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject
    lateinit var tdlibClient: TdlibClient

    /**
     * Never read: injecting creates every caching repository, which subscribes to TDLib's updates as it is created.
     * TDLib introduces each user and chat only once, and its updates are not replayed, so this must happen before
     * [tdlibClient] starts. Hilt injects both fields in `super.onCreate()`.
     */
    @Inject
    lateinit var updateCaches: Set<@JvmSuppressWildcards TdlibUpdateCache>

    /** Coil's image loader (`ImageLoaderModule`), created when Coil first asks for it in [newImageLoader]. */
    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    override fun onCreate() {
        super.onCreate()
        // Creates the TDLib client and sends its parameters on a background dispatcher, so TDLib is ready by the
        // first frame (docs/PERFORMANCE.md §2); returns immediately.
        tdlibClient.start()
    }

    /** Called by Coil, once, when the first image is shown. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
