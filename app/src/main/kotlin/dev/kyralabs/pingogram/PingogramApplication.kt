package dev.kyralabs.pingogram

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import javax.inject.Inject

@HiltAndroidApp
class PingogramApplication : Application() {
    @Inject
    lateinit var tdlibClient: TdlibClient

    /**
     * Never read: injecting creates every caching repository, which subscribes to TDLib's updates as it is created.
     * TDLib introduces each user and chat only once, and its updates are not replayed, so this must happen before
     * [tdlibClient] starts. Hilt injects both fields in `super.onCreate()`.
     */
    @Inject
    lateinit var updateCaches: Set<@JvmSuppressWildcards TdlibUpdateCache>

    override fun onCreate() {
        super.onCreate()
        // Creates the TDLib client and sends its parameters on a background dispatcher, so TDLib is ready by the
        // first frame (docs/PERFORMANCE.md §2); returns immediately.
        tdlibClient.start()
    }
}
