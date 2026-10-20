package dev.kyralabs.pingogram.feature.chat.ui.location

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.LocationAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [ShareLocationViewModel]: opening the sheet (or the snackbar when the location is off), the permission before Android
 * asks and once it no longer does, the search while the sheet shows and is collected, not finding a location, and
 * what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareLocationViewModelTest {
    private val repository = FakeDeviceLocationRepository()
    private val here = DeviceLocation(Location(51.4978, -0.1745), accuracyMeters = 12f)
    private val rough = DeviceLocation(Location(51.4979, -0.1744), accuracyMeters = 70f, isCached = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        ShareLocationViewModel(repository, savedState)

    /** Opens the sheet of [viewModel] and checks it searches, with the uiState turbine at its newest item. */
    private suspend fun TestScope.openSearching(
        viewModel: ShareLocationViewModel,
        states: ReceiveTurbine<ShareLocationUiState>,
    ) {
        viewModel.onEvent(ShareLocationEvent.Open)
        assertEquals(ShareLocationUiState.Locating(), states.awaitItem())
        runCurrent()
    }

    @Test
    fun `closed at first, without searching`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(ShareLocationUiState.Closed, awaitItem())
                runCurrent()
                expectNoEvents()
            }
            assertEquals(0, repository.searches)
        }

    @Test
    fun `opened with the location allowed, it searches and shows each better location`() =
        runTest {
            val viewModel = viewModel()

            viewModel.uiState.test {
                assertEquals(ShareLocationUiState.Closed, awaitItem())
                openSearching(viewModel, this)
                repository.send(rough)
                assertEquals(ShareLocationUiState.Locating(rough), awaitItem())
                repository.send(here)
                assertEquals(ShareLocationUiState.Locating(here), awaitItem())
            }
        }

    @Test
    fun `dismissing closes the sheet and stops the search`() =
        runTest {
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                openSearching(viewModel, this)
                viewModel.onEvent(ShareLocationEvent.Dismiss)
                assertEquals(ShareLocationUiState.Closed, awaitItem())
                runCurrent()
                assertEquals(0, repository.searching)
            }
        }

    @Test
    fun `the search stops a while after the screen stops collecting, and starts again on return`() =
        runTest {
            val viewModel = viewModel()
            viewModel.uiState.test {
                awaitItem()
                openSearching(viewModel, this)
            }
            assertEquals(1, repository.searching)

            advanceTimeBy(ShareLocationViewModel.STOP_TIMEOUT_MILLIS + 1)
            assertEquals(0, repository.searching)

            viewModel.uiState.test {
                assertEquals(ShareLocationUiState.Locating(), awaitItem())
                runCurrent()
                assertEquals(1, repository.searching)
                assertEquals(2, repository.searches)
            }
        }

    @Test
    fun `an approximate location is searched as such, and the precise one can be asked for`() =
        runTest {
            repository.access = LocationAccess.Approximate
            val viewModel = viewModel()

            turbineScope {
                val states = viewModel.uiState.testIn(backgroundScope)
                val effects = viewModel.effects.testIn(backgroundScope)
                states.awaitItem()
                viewModel.onEvent(ShareLocationEvent.Open)
                assertEquals(ShareLocationUiState.Locating(isApproximate = true), states.awaitItem())
                viewModel.onEvent(ShareLocationEvent.AskPrecise)
                assertEquals(ShareLocationEffect.RequestPermission, effects.awaitItem())
            }
        }

    @Test
    fun `without the permission it says why, then Android asks, and the search starts once allowed`() =
        runTest {
            repository.access = LocationAccess.None
            val viewModel = viewModel()

            turbineScope {
                val states = viewModel.uiState.testIn(backgroundScope)
                val effects = viewModel.effects.testIn(backgroundScope)
                states.awaitItem()
                viewModel.onEvent(ShareLocationEvent.Open)
                assertEquals(ShareLocationUiState.Permission(), states.awaitItem())
                viewModel.onEvent(ShareLocationEvent.Allow)
                assertEquals(ShareLocationEffect.RequestPermission, effects.awaitItem())

                repository.access = LocationAccess.Precise
                viewModel.onEvent(ShareLocationEvent.PermissionResult(canAskAgain = false))
                assertEquals(ShareLocationUiState.Locating(), states.awaitItem())
            }
        }

    @Test
    fun `refused once it asks again, refused for good it opens the settings`() =
        runTest {
            repository.access = LocationAccess.None
            val viewModel = viewModel()

            turbineScope {
                val states = viewModel.uiState.testIn(backgroundScope)
                val effects = viewModel.effects.testIn(backgroundScope)
                states.awaitItem()
                viewModel.onEvent(ShareLocationEvent.Open)
                states.awaitItem()
                viewModel.onEvent(ShareLocationEvent.PermissionResult(canAskAgain = true))
                runCurrent()
                states.expectNoEvents()

                viewModel.onEvent(ShareLocationEvent.PermissionResult(canAskAgain = false))
                assertEquals(ShareLocationUiState.Permission(deniedForGood = true), states.awaitItem())
                viewModel.onEvent(ShareLocationEvent.Allow)
                assertEquals(ShareLocationEffect.OpenAppSettings, effects.awaitItem())
            }
        }

    @Test
    fun `allowed in the settings, the search starts when the screen is back`() =
        runTest {
            repository.access = LocationAccess.None
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onEvent(ShareLocationEvent.Open)
                awaitItem()
                repository.access = LocationAccess.Precise
                viewModel.onEvent(ShareLocationEvent.Started)
                assertEquals(ShareLocationUiState.Locating(), awaitItem())
            }
        }

    @Test
    fun `with the location off no sheet opens, and the snackbar offers the settings`() =
        runTest {
            repository.enabled = false
            val viewModel = viewModel()

            turbineScope {
                val states = viewModel.uiState.testIn(backgroundScope)
                val effects = viewModel.effects.testIn(backgroundScope)
                states.awaitItem()
                viewModel.onEvent(ShareLocationEvent.Open)
                assertEquals(ShareLocationEffect.LocationOff, effects.awaitItem())
                runCurrent()
                states.expectNoEvents()
            }
        }

    @Test
    fun `allowed with the location off, the sheet closes with the snackbar`() =
        runTest {
            repository.access = LocationAccess.None
            repository.enabled = false
            val viewModel = viewModel()

            turbineScope {
                val states = viewModel.uiState.testIn(backgroundScope)
                val effects = viewModel.effects.testIn(backgroundScope)
                states.awaitItem()
                viewModel.onEvent(ShareLocationEvent.Open)
                assertEquals(ShareLocationUiState.Permission(), states.awaitItem())
                repository.access = LocationAccess.Approximate
                viewModel.onEvent(ShareLocationEvent.PermissionResult(canAskAgain = true))
                assertEquals(ShareLocationEffect.LocationOff, effects.awaitItem())
                assertEquals(ShareLocationUiState.Closed, states.awaitItem())
            }
        }

    @Test
    fun `turned off while searching, the sheet closes with the snackbar`() =
        runTest {
            val viewModel = viewModel()

            turbineScope {
                val states = viewModel.uiState.testIn(backgroundScope)
                val effects = viewModel.effects.testIn(backgroundScope)
                states.awaitItem()
                openSearching(viewModel, states)
                repository.send(here)
                states.awaitItem()
                repository.enabled = false
                repository.end()
                assertEquals(ShareLocationEffect.LocationOff, effects.awaitItem())
                assertEquals(ShareLocationUiState.Closed, states.awaitItem())
            }
        }

    @Test
    fun `no first location in time stops the search as not found, and trying again searches anew`() =
        runTest {
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                openSearching(viewModel, this)
                advanceTimeBy(ShareLocationViewModel.FIRST_LOCATION_TIMEOUT.inWholeMilliseconds + 1)
                assertEquals(ShareLocationUiState.NotFound, awaitItem())
                assertEquals(0, repository.searching)

                viewModel.onEvent(ShareLocationEvent.Retry)
                assertEquals(ShareLocationUiState.Locating(), awaitItem())
                runCurrent()
                assertEquals(2, repository.searches)
            }
        }

    @Test
    fun `without a provider to ask the location is not found`() =
        runTest {
            repository.hasProvider = false
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem()
                viewModel.onEvent(ShareLocationEvent.Open)
                assertEquals(ShareLocationUiState.Locating(), awaitItem())
                assertEquals(ShareLocationUiState.NotFound, awaitItem())
            }
        }

    @Test
    fun `open in maps shows the location found, and nothing before`() =
        runTest {
            val viewModel = viewModel()

            turbineScope {
                val states = viewModel.uiState.testIn(backgroundScope)
                val effects = viewModel.effects.testIn(backgroundScope)
                states.awaitItem()
                openSearching(viewModel, states)
                viewModel.onEvent(ShareLocationEvent.OpenInMaps)
                runCurrent()
                effects.expectNoEvents()

                repository.send(here)
                states.awaitItem()
                viewModel.onEvent(ShareLocationEvent.OpenInMaps)
                assertEquals(ShareLocationEffect.OpenInMaps(here), effects.awaitItem())
            }
        }

    @Test
    fun `after process death the sheet opens again where it was, with the access read anew`() =
        runTest {
            repository.access = LocationAccess.None
            val savedState = SavedStateHandle()
            val first = viewModel(savedState)
            first.onEvent(ShareLocationEvent.Open)
            first.onEvent(ShareLocationEvent.PermissionResult(canAskAgain = false))

            viewModel(savedState).uiState.test {
                assertEquals(ShareLocationUiState.Closed, awaitItem())
                assertEquals(ShareLocationUiState.Permission(deniedForGood = true), awaitItem())
            }
            repository.access = LocationAccess.Precise
            viewModel(savedState).uiState.test {
                assertEquals(ShareLocationUiState.Closed, awaitItem())
                assertEquals(ShareLocationUiState.Locating(), awaitItem())
            }
        }

    @Test
    fun `a location improves until it is accurate, an approximate one never says it does`() {
        assertTrue(ShareLocationUiState.Locating().isRefining)
        assertTrue(ShareLocationUiState.Locating(rough).isRefining)
        assertTrue(ShareLocationUiState.Locating(here.copy(accuracyMeters = null)).isRefining)
        assertFalse(ShareLocationUiState.Locating(here).isRefining)
        assertFalse(ShareLocationUiState.Locating(rough, isApproximate = true).isRefining)
    }
}
