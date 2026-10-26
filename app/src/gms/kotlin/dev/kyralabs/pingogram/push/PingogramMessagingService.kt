package dev.kyralabs.pingogram.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.domain.repository.DevicePushRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Receives Firebase Cloud Messaging's pushes and tokens (`docs/adr/0004-push-delivery.md`). The process may have been
 * started for it: `PingogramApplication` has then already started TDLib and the notification renderer, so a push only
 * has to reach TDLib, which turns it into notifications the renderer shows. It goes to the account it is for, whichever
 * the screens show (`docs/adr/0031-multiple-accounts.md`).
 *
 * Firebase calls both methods on a thread of its own, one at a time, and lets the process go once they return, so
 * [onMessageReceived] waits for TDLib, as long as [DevicePushRepository.processPush] allows (a few seconds).
 */
@AndroidEntryPoint
class PingogramMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushRepository: DevicePushRepository

    override fun onCreate() {
        // Before Firebase's own start of the service, which asks for its configuration (FirebaseStart).
        FirebaseStart.ensure(this)
        super.onCreate()
    }

    override fun onNewToken(token: String) {
        pushRepository.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Firebase's worker thread, never the main thread; the wait is bounded by processPush.
        runBlocking { pushRepository.processPush(message.data, message.sentTime, message.notification?.sound) }
    }

    // onDeletedMessages: Firebase dropped pushes it could not deliver. Nothing to do: TDLib fetches everything new
    // when it next connects, and its notification engine shows what is still unread.
}
