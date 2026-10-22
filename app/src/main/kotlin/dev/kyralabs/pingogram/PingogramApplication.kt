package dev.kyralabs.pingogram

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import dev.kyralabs.pingogram.background.BackgroundConnection
import dev.kyralabs.pingogram.background.TransfersController
import dev.kyralabs.pingogram.core.data.diagnostics.CrashRecorder
import dev.kyralabs.pingogram.core.data.repository.TdlibNetworkTypeReporter
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.notifications.NotificationRenderer
import dev.kyralabs.pingogram.core.notifications.ShareTargetShortcuts
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.feature.calls.Calls
import dev.kyralabs.pingogram.widget.RecentChatsWidgetUpdater
import javax.inject.Inject

@HiltAndroidApp
class PingogramApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    @Inject
    lateinit var tdlibClient: TdlibClient

    /**
     * Never read: injecting creates every caching repository, which subscribes to TDLib's updates as it is created.
     * TDLib introduces each user and chat only once, and its updates are not replayed, so this must happen before
     * [tdlibClient] starts. Hilt injects both fields in `super.onCreate()`.
     */
    @Inject
    lateinit var updateCaches: Set<@JvmSuppressWildcards TdlibUpdateCache>

    /** Shows TDLib's notifications in every process, the ones a notification action starts too. */
    @Inject
    lateinit var notificationRenderer: NotificationRenderer

    /** Offers the most recent chats in the system's share sheet (Direct Share targets, FR-N2). */
    @Inject
    lateinit var shareTargetShortcuts: ShareTargetShortcuts

    /** Keeps the home screen widget of recent chats current while one is placed. */
    @Inject
    lateinit var recentChatsWidgetUpdater: RecentChatsWidgetUpdater

    /** Keeps new messages arriving in the background as far as the flavor can: "Keep connected" (foss), FCM (gms). */
    @Inject
    lateinit var backgroundConnection: BackgroundConnection

    /** Keeps the app running while the files the user sends and downloads are on their way (FR-N4). */
    @Inject
    lateinit var transfersController: TransfersController

    /** Rings for incoming calls and runs the user's call, in every process (FR-V1). */
    @Inject
    lateinit var calls: Calls

    /** Tells TDLib about every new network the device uses, so it reconnects at once (docs/PERFORMANCE.md §3). */
    @Inject
    lateinit var networkTypeReporter: TdlibNetworkTypeReporter

    /** Creates the workers with Hilt (notification actions, transfers); WorkManager asks for it when first used. */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Coil's image loader (`ImageLoaderModule`), created when Coil first asks for it in [newImageLoader]. */
    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    override fun onCreate() {
        // Keeps the last crash on the device for "Send feedback", which the user shares themselves; first, so a crash
        // while the app starts is kept too.
        CrashRecorder(this).install()
        super.onCreate()
        // Creates the notification channels and follows TDLib's notifications, which it restores right after it starts.
        notificationRenderer.start()
        // Publishes the most recent chats as conversation shortcuts the share sheet lists, while logged in.
        shareTargetShortcuts.start()
        // Redraws the home screen widget when the chats change, only while a widget is placed.
        recentChatsWidgetUpdater.start()
        // foss: starts or stops the "Keep connected" service as its setting and the login change; gms: registers the
        // push token with Telegram at every login and deletes it at logout.
        backgroundConnection.start()
        // Starts the transfers' foreground service while the user's uploads and downloads run; WorkManager only then.
        transfersController.start()
        // Sends TDLib the network type once it has its parameters, and again at every new network.
        networkTypeReporter.start()
        // Follows TDLib's calls, which it announces only once: an incoming call rings from the first update on.
        calls.start()
        // Creates the TDLib client and sends its parameters on a background dispatcher, so TDLib is ready by the
        // first frame (docs/PERFORMANCE.md §2); returns immediately.
        tdlibClient.start()
    }

    /**
     * WorkManager's configuration, read when it is first used rather than at startup: the manifest removes its
     * initializer, so the process starts without it (docs/PERFORMANCE.md §2).
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Called by Coil, once, when the first image is shown. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
