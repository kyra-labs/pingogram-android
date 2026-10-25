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
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.diagnostics.CrashRecorder
import dev.kyralabs.pingogram.core.data.repository.TdlibNetworkTypeReporter
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.notifications.NotificationRenderer
import dev.kyralabs.pingogram.core.notifications.ShareTargetShortcuts
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.ui.theme.warmUpGoogleSansFlex
import dev.kyralabs.pingogram.feature.calls.Calls
import dev.kyralabs.pingogram.startup.StartupTrace
import dev.kyralabs.pingogram.startup.asyncTraceSection
import dev.kyralabs.pingogram.widget.RecentChatsWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    lateinit var notificationRenderer: dagger.Lazy<NotificationRenderer>

    /** Offers the most recent chats in the system's share sheet (Direct Share targets, FR-N2). */
    @Inject
    lateinit var shareTargetShortcuts: dagger.Lazy<ShareTargetShortcuts>

    /** Keeps the home screen widget of recent chats current while one is placed. */
    @Inject
    lateinit var recentChatsWidgetUpdater: dagger.Lazy<RecentChatsWidgetUpdater>

    /** Keeps new messages arriving in the background as far as the flavor can: "Keep connected" (foss), FCM (gms). */
    @Inject
    lateinit var backgroundConnection: dagger.Lazy<BackgroundConnection>

    /** Keeps the app running while the files the user sends and downloads are on their way (FR-N4). */
    @Inject
    lateinit var transfersController: dagger.Lazy<TransfersController>

    /** Rings for incoming calls and runs the user's call, in every process (FR-V1). */
    @Inject
    lateinit var calls: dagger.Lazy<Calls>

    /** Tells TDLib about every new network the device uses, so it reconnects at once (docs/PERFORMANCE.md §3). */
    @Inject
    lateinit var networkTypeReporter: dagger.Lazy<TdlibNetworkTypeReporter>

    /** Creates the workers with Hilt (notification actions, transfers); WorkManager asks for it when first used. */
    @Inject
    lateinit var workerFactory: dagger.Lazy<HiltWorkerFactory>

    /** Coil's image loader (`ImageLoaderModule`), created when Coil first asks for it in [newImageLoader]. */
    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    /** Its history of chats nobody looks at goes when Android asks for memory back ([onTrimMemory]). */
    @Inject
    lateinit var messageRepository: dagger.Lazy<MessageRepository>

    /**
     * The Appearance settings, read as the app starts: they and the passcode lock's, in the same file, are what the
     * splash screen waits for, and the first activity finds them in memory (docs/PERFORMANCE.md §2).
     */
    @Inject
    lateinit var appearanceRepository: dagger.Lazy<AppearanceRepository>

    /** Where work that must not hold up the first frame runs: the `Default` dispatcher. */
    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        // Keeps the last crash on the device for "Send feedback", which the user shares themselves; first, so a crash
        // while the app starts is kept too.
        CrashRecorder(this).install()
        // Injects the fields: TDLib's client and the caching repositories, which subscribe to its updates now; the rest
        // is lazy, made by startInBackground.
        StartupTrace.section(StartupTrace.INJECT) { super.onCreate() }
        // Reads the settings file while the activity is created: the splash screen waits for it (main.awaitSettings).
        applicationScope.launch {
            asyncTraceSection(StartupTrace.READ_SETTINGS) { appearanceRepository.get().appearance.first() }
        }
        // Parses the font and makes its typefaces while the activity is created, instead of in the first frame.
        applicationScope.launch { warmUpGoogleSansFlex(this@PingogramApplication) }
        startInBackground()
        // Creates the TDLib client and sends its parameters on a background dispatcher, so TDLib is ready by the
        // first frame (docs/PERFORMANCE.md §2); returns immediately.
        StartupTrace.section(StartupTrace.TDLIB_START) { tdlibClient.start() }
    }

    /**
     * Makes and starts what follows TDLib's state in every process, on the `Default` dispatcher, off the main thread
     * and out of the way of the activity (docs/PERFORMANCE.md §2). Each follows a repository that keeps TDLib's state
     * (a [TdlibUpdateCache]), so starting a little after TDLib loses nothing: an incoming call still rings, a
     * restored notification still shows.
     */
    private fun startInBackground() {
        applicationScope.launch {
            StartupTrace.section(StartupTrace.STARTERS) {
                // Creates the notification channels and follows TDLib's notifications.
                notificationRenderer.get().start()
                // Follows TDLib's calls: an incoming call rings from the first update on.
                calls.get().start()
                // Sends TDLib the network type once it has its parameters, and again at every new network.
                networkTypeReporter.get().start()
                // foss: starts or stops the "Keep connected" service as its setting and the login change; gms:
                // registers the push token with Telegram at every login and deletes it at logout.
                backgroundConnection.get().start()
                // Starts the transfers' foreground service while the user's uploads and downloads run.
                transfersController.get().start()
                // Publishes the most recent chats as conversation shortcuts the share sheet lists, while logged in.
                shareTargetShortcuts.get().start()
                // Redraws the home screen widget when the chats change, only while a widget is placed.
                recentChatsWidgetUpdater.get().start()
            }
        }
    }

    /**
     * Once the app's screens are out of sight, and whenever Android runs short of memory after that, the history of the
     * chats no screen shows is dropped (docs/PERFORMANCE.md §3); TDLib's database gives it back at once when a chat
     * opens again. Coil trims its own memory cache.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) messageRepository.get().trimMemory()
    }

    /**
     * WorkManager's configuration, read when it is first used rather than at startup: the manifest removes its
     * initializer, so the process starts without it (docs/PERFORMANCE.md §2).
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory.get()).build()

    /** Called by Coil, once, when the first image is shown. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
