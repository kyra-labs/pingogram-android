package dev.kyralabs.pingogram.background

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.data.di.Accounts
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.repository.KeepConnectedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs "Keep connected" (`foss`, `docs/adr/0003-keep-connected-foreground-service.md`): [KeepConnectedService] runs
 * while the setting is on and an account is logged in, so TDLib's connection stays open in the background. Any account
 * of the device counts (`docs/adr/0031-multiple-accounts.md`): the service keeps the process, in which every account's
 * client runs.
 *
 * - It is started only while the app is in the foreground ([foreground]): Android 12+ refuses to start foreground
 *   services from the background, except for the exemptions [KeepConnectedBootReceiver] uses after a reboot or an
 *   update. Once running, it stays when the app leaves the foreground; that is its purpose.
 * - It stops as soon as the setting is off or nobody is logged in (logging out, here or from another device); stopping
 *   is allowed at any time.
 * - The boot receiver is enabled exactly while the setting is on, so Pingogram starts after a reboot only for users who
 *   asked for it.
 *
 * Started once per process by `PingogramApplication`, on the application scope.
 */
@Singleton
class KeepConnectedController internal constructor(
    private val keepConnectedRepository: KeepConnectedRepository,
    /** The device's accounts' login steps together: signed in while one is. */
    private val authSteps: Flow<AuthStep>,
    private val foreground: Flow<Boolean>,
    private val service: KeepConnectedServiceControl,
    private val scope: CoroutineScope,
) : BackgroundConnection {
    @Inject
    internal constructor(
        keepConnectedRepository: KeepConnectedRepository,
        accounts: Accounts,
        service: AndroidKeepConnectedServiceControl,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        keepConnectedRepository,
        accounts.combinedAuthSteps(),
        ProcessLifecycleOwner
            .get()
            .lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) },
        service,
        scope,
    )

    override fun start() {
        scope.launch {
            combine(
                keepConnectedRepository.keepConnected,
                authSteps.map { it.toSession() }.distinctUntilChanged(),
                foreground.distinctUntilChanged(),
            ) { enabled, session, inForeground -> Triple(enabled, session, inForeground) }
                .distinctUntilChanged()
                .collect { (enabled, session, inForeground) ->
                    service.setStartsAfterBoot(enabled)
                    when {
                        !enabled || session == Session.SignedOut -> service.stop()

                        session == Session.LoggedIn && inForeground -> service.start()

                        // In the background, or TDLib still starting: it keeps running if it runs, and starts later.
                        else -> Unit
                    }
                }
        }
    }

    private enum class Session { Unknown, LoggedIn, SignedOut }

    private fun AuthStep.toSession(): Session =
        when (this) {
            AuthStep.Initializing -> Session.Unknown
            AuthStep.Ready -> Session.LoggedIn
            else -> Session.SignedOut
        }
}
