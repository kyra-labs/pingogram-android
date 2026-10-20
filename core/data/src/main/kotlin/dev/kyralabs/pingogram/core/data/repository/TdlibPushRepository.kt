package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.error.toOutcome
import dev.kyralabs.pingogram.core.domain.repository.NotificationRepository
import dev.kyralabs.pingogram.core.domain.repository.PushRepository
import dev.kyralabs.pingogram.core.domain.repository.PushTokenSource
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * [PushRepository] on TDLib (`docs/adr/0004-push-delivery.md`), with the push service of the flavor, if it has one
 * ([PushTokenSource], bound in `gms` only; `foss` leaves the `Optional` empty and this inert).
 *
 * - **Registering**: at every login, and at every start while logged in, the source's current token goes to
 *   `registerDevice` as `deviceTokenFirebaseCloudMessaging` with `encrypt`, so Telegram encrypts each push with a
 *   key only this TDLib database holds; a token the push service announces later ([onNewToken]) follows at once.
 *   TDLib keeps the token in its database, sends it again at each start by itself (as Telegram recommends) and
 *   answers a registration of the token it already has without asking Telegram, so this costs nothing at an ordinary
 *   start. One account per app: no other user ids. A `FLOOD_WAIT` is waited out; other failures are dropped, the next
 *   start registers again.
 * - **Logout** (Ready to anything else): the token is deleted at the push service. TDLib does not unregister by
 *   itself, but logging out ends the session Telegram registered the token for, and TDLib deletes its database with
 *   the key; deleting the token makes sure no push reaches the device even so.
 * - **A push** ([processPush]) goes to `processPushNotification`, which TDLib accepts before it is fully started (it
 *   waits for its database) and answers once its notification updates are out.
 */
@Singleton
class TdlibPushRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope private val scope: CoroutineScope,
        private val notificationRepository: NotificationRepository,
        tokenSource: Optional<PushTokenSource>,
    ) : PushRepository {
        private val tokenSource: PushTokenSource? = tokenSource.getOrNull()

        /** The token to register while logged in: the source's at login, then each new one; null after a logout. */
        private val token = MutableStateFlow<String?>(null)

        private val started = AtomicBoolean(false)

        override fun start() {
            val source = tokenSource ?: return
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                var wasLoggedIn = false
                client.authState
                    .map { it == AuthState.Ready }
                    .distinctUntilChanged()
                    .collectLatest { loggedIn ->
                        if (loggedIn) {
                            wasLoggedIn = true
                            source.token()?.let { token.value = it }
                            // Cancelled by a logout, and a newer token cancels the registration of an older one.
                            token.filterNotNull().collectLatest(::register)
                        } else if (wasLoggedIn) {
                            wasLoggedIn = false
                            token.value = null
                            source.deleteToken()
                        }
                    }
            }
        }

        override fun onNewToken(token: String) {
            if (tokenSource != null) this.token.value = token
        }

        override suspend fun processPush(payload: String): Outcome<Unit> {
            val processed =
                withTimeoutOrNull(PUSH_TIMEOUT) {
                    client.send(TdApi.ProcessPushNotification(payload)).toOutcome().map {}
                } ?: return Outcome.Failure(DomainError.Network)
            val needsSync = processed is Outcome.Failure && processed.error == DomainError.Silent
            if (processed is Outcome.Success || needsSync) {
                withTimeoutOrNull(SETTLE_TIMEOUT) {
                    // TDLib error 406: the push carried nothing TDLib can show; it connects and fetches what is new.
                    if (needsSync) client.connectionState.first { it == ConnectionState.Ready }
                    // Notifications TDLib holds back for a moment (another device of the user is online).
                    notificationRepository.hasPendingNotifications.first { !it }
                }
            }
            return processed
        }

        private suspend fun register(token: String) {
            val deviceToken = TdApi.DeviceTokenFirebaseCloudMessaging(token, true)
            repeat(MAX_ATTEMPTS) {
                val outcome = client.request(TdApi.RegisterDevice(deviceToken, LongArray(0)))
                val floodWait = (outcome as? Outcome.Failure)?.error as? DomainError.FloodWait ?: return
                delay(floodWait.seconds.seconds)
            }
        }

        internal companion object {
            /**
             * How long TDLib may take for a push, its start included. A push service gives an app a few seconds
             * (Firebase: "For long-running tasks (10 seconds or more) use WorkManager"), and [SETTLE_TIMEOUT] follows.
             */
            val PUSH_TIMEOUT = 6.seconds

            /** How long a push may wait for TDLib's connection or held-back notifications afterwards. */
            val SETTLE_TIMEOUT = 3.seconds

            /** Registrations, each after the `FLOOD_WAIT` of the one before. */
            const val MAX_ATTEMPTS = 3
        }
    }
