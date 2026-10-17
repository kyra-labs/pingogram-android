package dev.kyralabs.pingogram

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import javax.inject.Inject

@HiltAndroidApp
class PingogramApplication : Application() {
    @Inject
    lateinit var tdlibClient: TdlibClient

    override fun onCreate() {
        super.onCreate()
        // Creates the TDLib client and sends its parameters on a background dispatcher, so TDLib is ready by the
        // first frame (docs/PERFORMANCE.md §2); returns immediately.
        tdlibClient.start()
    }
}
