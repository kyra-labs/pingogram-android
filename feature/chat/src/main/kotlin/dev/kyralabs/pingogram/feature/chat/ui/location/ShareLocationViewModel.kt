package dev.kyralabs.pingogram.feature.chat.ui.location

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.LocationAccess
import dev.kyralabs.pingogram.core.domain.repository.DeviceLocationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * The "Location" sheet of the conversation's attach menu (`REQUIREMENTS.md` FR-C5): the device's location, found while
 * the sheet shows, for the conversation to send (`ChatEvent.SendLocation`, which takes the composer's reply along).
 *
 * - **Opening** ([ShareLocationEvent.Open]): with the location allowed but turned off, no sheet opens and a snackbar
 *   offers the location settings ([ShareLocationEffect.LocationOff]). Without the location allowed, the sheet first says
 *   why Pingogram asks ([ShareLocationUiState.Permission]), then Android asks ([ShareLocationEffect.RequestPermission]);
 *   once Android no longer asks, the button opens the app's settings instead. An approximate location (Android 12+) is
 *   enough: it is what is sent, and the sheet offers to ask for the precise one.
 * - **Searching** ([ShareLocationUiState.Locating]): [DeviceLocationRepository.locate] runs only while the sheet is open
 *   and the screen collects [uiState] (it stops [STOP_TIMEOUT_MILLIS] after the app goes to the background, and starts
 *   again on return), showing the best location so far. Without a first location within [FIRST_LOCATION_TIMEOUT], or
 *   with no provider to ask, it stops: [ShareLocationUiState.NotFound], with "Try again". The location turned off
 *   meanwhile closes the sheet with the snackbar.
 * - **Process death**: whether the sheet is open, and whether Android no longer asks, are kept in [savedState]; the
 *   search starts again.
 */
@HiltViewModel
class ShareLocationViewModel
    @Inject
    constructor(
        private val repository: DeviceLocationRepository,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val open = MutableStateFlow(savedState[KEY_OPEN] ?: false)
        private val deniedForGood = MutableStateFlow(savedState[KEY_DENIED_FOR_GOOD] ?: false)
        private val access = MutableStateFlow(LocationAccess.None)

        /** Counts "Try again", which starts a new search. */
        private val attempt = MutableStateFlow(0)

        private val effectChannel = Channel<ShareLocationEffect>(Channel.BUFFERED)

        /** The effects to handle once each: asking for the permission, settings, the snackbar, a maps app. */
        val effects: Flow<ShareLocationEffect> = effectChannel.receiveAsFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<ShareLocationUiState> =
            combine(open, access, deniedForGood, attempt) { isOpen, access, deniedForGood, _ ->
                Step(isOpen, access, deniedForGood)
            }.flatMapLatest { step ->
                when {
                    !step.isOpen -> flowOf(ShareLocationUiState.Closed)
                    step.access == LocationAccess.None -> flowOf(ShareLocationUiState.Permission(step.deniedForGood))
                    else -> locating(isApproximate = step.access == LocationAccess.Approximate)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ShareLocationUiState.Closed)

        init {
            // Restored after process death: the access is read again before anything shows.
            if (open.value) access.value = repository.access()
        }

        fun onEvent(event: ShareLocationEvent) {
            when (event) {
                ShareLocationEvent.Open -> {
                    access.value = repository.access()
                    if (!locationOff()) setOpen(true)
                }

                ShareLocationEvent.Started -> {
                    if (open.value) {
                        access.value = repository.access()
                        if (locationOff()) setOpen(false)
                    }
                }

                ShareLocationEvent.Allow -> {
                    val refused = deniedForGood.value
                    effectChannel.trySend(
                        if (refused) ShareLocationEffect.OpenAppSettings else ShareLocationEffect.RequestPermission,
                    )
                }

                ShareLocationEvent.AskPrecise -> {
                    effectChannel.trySend(ShareLocationEffect.RequestPermission)
                }

                is ShareLocationEvent.PermissionResult -> {
                    permissionResult(event.canAskAgain)
                }

                ShareLocationEvent.Retry -> {
                    attempt.value++
                }

                ShareLocationEvent.OpenInMaps -> {
                    val location = (uiState.value as? ShareLocationUiState.Locating)?.location ?: return
                    effectChannel.trySend(ShareLocationEffect.OpenInMaps(location))
                }

                ShareLocationEvent.Dismiss -> {
                    setOpen(false)
                }
            }
        }

        /** Android answered: a refusal for good turns the button into settings; a location turned off closes the sheet. */
        private fun permissionResult(canAskAgain: Boolean) {
            access.value = repository.access()
            if (access.value == LocationAccess.None) {
                setDeniedForGood(!canAskAgain)
            } else {
                setDeniedForGood(false)
                if (locationOff()) setOpen(false)
            }
        }

        /**
         * Whether the location is allowed but turned off, which the snackbar then says. Without the permission the sheet
         * asks for it first.
         */
        private fun locationOff(): Boolean {
            val off = access.value != LocationAccess.None && !repository.isLocationEnabled()
            if (off) effectChannel.trySend(ShareLocationEffect.LocationOff)
            return off
        }

        /**
         * The search: searching at once, then each better location. No first location within [FIRST_LOCATION_TIMEOUT]
         * stops it as not found; so does a search that ends without one. A search that ends because the location was
         * turned off closes the sheet with the snackbar; one that ends otherwise keeps what it found.
         */
        private fun locating(isApproximate: Boolean): Flow<ShareLocationUiState> =
            channelFlow {
                send(ShareLocationUiState.Locating(isApproximate = isApproximate))
                val first = CompletableDeferred<Unit>()
                val search =
                    launch {
                        repository.locate().collect { location ->
                            first.complete(Unit)
                            send(ShareLocationUiState.Locating(location, isApproximate))
                        }
                    }
                val found =
                    withTimeoutOrNull(FIRST_LOCATION_TIMEOUT) {
                        select {
                            first.onAwait { true }
                            search.onJoin { false }
                        }
                    }
                if (found == null) {
                    search.cancel()
                    send(ShareLocationUiState.NotFound)
                    return@channelFlow
                }
                search.join()
                if (!repository.isLocationEnabled()) {
                    effectChannel.trySend(ShareLocationEffect.LocationOff)
                    setOpen(false)
                } else if (!found) {
                    send(ShareLocationUiState.NotFound)
                }
            }

        private fun setOpen(isOpen: Boolean) {
            open.value = isOpen
            savedState[KEY_OPEN] = isOpen
        }

        private fun setDeniedForGood(denied: Boolean) {
            deniedForGood.value = denied
            savedState[KEY_DENIED_FOR_GOOD] = denied
        }

        private data class Step(
            val isOpen: Boolean,
            val access: LocationAccess,
            val deniedForGood: Boolean,
        )

        internal companion object {
            /** How long the search keeps the providers on after the screen stops collecting, e.g. for a rotation. */
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** How long the search waits for a first location: GPS takes up to about this long from cold outdoors. */
            val FIRST_LOCATION_TIMEOUT = 30.seconds

            const val KEY_OPEN = "location.open"
            const val KEY_DENIED_FOR_GOOD = "location.deniedForGood"
        }
    }
