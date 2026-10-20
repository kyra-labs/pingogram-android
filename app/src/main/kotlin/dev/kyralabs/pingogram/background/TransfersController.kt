package dev.kyralabs.pingogram.background

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.repository.TransferRepository
import dev.kyralabs.pingogram.core.notifications.TransfersWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts [TransfersWorker], which keeps the app running while the files the user sends and downloads are on their way
 * (`REQUIREMENTS.md` FR-N4), whenever such transfers run while the app is in the foreground ([foreground]): as they
 * begin, the user is in the app; and when the user comes back while they still run and the worker could not stay
 * (Android 12+ refused it from the background, Android 15's daily `dataSync` time was up). The worker ends by itself
 * once they are over. Started once per process by `PingogramApplication`, on the application scope.
 */
@Singleton
class TransfersController internal constructor(
    private val transferRepository: TransferRepository,
    private val foreground: Flow<Boolean>,
    private val startWorker: () -> Unit,
    private val scope: CoroutineScope,
) {
    @Inject
    internal constructor(
        transferRepository: TransferRepository,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) : this(
        transferRepository,
        ProcessLifecycleOwner
            .get()
            .lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) },
        { TransfersWorker.start(context) },
        scope,
    )

    fun start() {
        scope.launch {
            combine(
                transferRepository.transfers.map { !it.isEmpty }.distinctUntilChanged(),
                foreground.distinctUntilChanged(),
            ) { transferring, inForeground -> transferring && inForeground }
                .distinctUntilChanged()
                .filter { it }
                .collect { startWorker() }
        }
    }
}
