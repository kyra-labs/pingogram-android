package dev.kyralabs.pingogram.feature.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.LinkSubject
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.domain.repository.LinkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.IOException

/** The QR code scanner: the camera's permission, and what a code's text leads to. */
@OptIn(ExperimentalCoroutinesApi::class)
class QrScannerViewModelTest {
    private val links = FakeLinkRepository()
    private val images = FakeImageReader()
    private val created = mutableListOf<QrScannerViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(
        purpose: ScanPurpose = ScanPurpose.Any,
        handle: SavedStateHandle = SavedStateHandle(),
    ) = QrScannerViewModel(purpose, handle, links, images).also { created += it }

    private fun QrScannerViewModel.started(granted: Boolean = true) =
        apply { onEvent(QrScannerEvent.Started(hasCamera = true, permissionGranted = granted)) }

    @Test
    fun `checks the camera until the screen says what Android allows`() {
        assertEquals(CameraAccess.Checking, viewModel().uiState.value.camera)
    }

    @Test
    fun `scans at once where the camera is allowed`() {
        val viewModel = viewModel().started()

        assertEquals(CameraAccess.Granted, viewModel.uiState.value.camera)
        assertTrue(viewModel.uiState.value.isScanning)
    }

    @Test
    fun `without a camera only an image scans`() {
        val viewModel = viewModel()

        viewModel.onEvent(QrScannerEvent.Started(hasCamera = false, permissionGranted = false))

        assertEquals(CameraAccess.NoCamera, viewModel.uiState.value.camera)
    }

    @Test
    fun `explains before Android asks, then asks`() =
        runTest {
            val viewModel = viewModel().started(granted = false)
            assertEquals(CameraAccess.Rationale(), viewModel.uiState.value.camera)

            viewModel.effects.test {
                viewModel.onEvent(QrScannerEvent.Continue)
                assertEquals(QrScannerEffect.RequestPermission, awaitItem())
            }
            viewModel.onEvent(QrScannerEvent.PermissionResult(granted = true, canAskAgain = false))
            assertEquals(CameraAccess.Granted, viewModel.uiState.value.camera)
        }

    @Test
    fun `refused for good, it offers the app's settings, also after process death`() =
        runTest {
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle = handle).started(granted = false)
            viewModel.onEvent(QrScannerEvent.PermissionResult(granted = false, canAskAgain = false))
            assertEquals(CameraAccess.Rationale(deniedForGood = true), viewModel.uiState.value.camera)

            viewModel.effects.test {
                viewModel.onEvent(QrScannerEvent.Continue)
                assertEquals(QrScannerEffect.OpenAppSettings, awaitItem())
            }
            val restored = viewModel(handle = handle).started(granted = false)
            assertEquals(CameraAccess.Rationale(deniedForGood = true), restored.uiState.value.camera)
        }

    @Test
    fun `a Telegram link opens its target`() =
        runTest {
            links.target = LinkTarget.UserProfile(USER)
            val viewModel = viewModel().started()

            viewModel.effects.test {
                viewModel.onEvent(QrScannerEvent.Scanned(" https://t.me/ada "))
                assertEquals(ScanResult.Resolving, viewModel.uiState.value.result)
                runCurrent()
                assertEquals(QrScannerEffect.Found, awaitItem())
                assertEquals(QrScannerEffect.Open(LinkTarget.UserProfile(USER)), awaitItem())
            }
            assertEquals(listOf("https://t.me/ada"), links.resolved)
        }

    @Test
    fun `text that is no Telegram link is never sent, and can be copied`() =
        runTest {
            val viewModel = viewModel().started()

            viewModel.effects.test {
                viewModel.onEvent(QrScannerEvent.Scanned("WIFI:S:Home;P:secret;;"))
                runCurrent()
                assertEquals(QrScannerEffect.Found, awaitItem())
                assertEquals(ScanResult.NotTelegramLink("WIFI:S:Home;P:secret;;"), viewModel.uiState.value.result)
                assertFalse(viewModel.uiState.value.isScanning)

                viewModel.onEvent(QrScannerEvent.Copy)
                assertEquals(QrScannerEffect.CopyText("WIFI:S:Home;P:secret;;"), awaitItem())
            }
            assertTrue(links.resolved.isEmpty())
        }

    @Test
    fun `a frame read while a result shows is ignored, and scanning again reads frames`() =
        runTest {
            val viewModel = viewModel().started()
            viewModel.onEvent(QrScannerEvent.Scanned("hello"))
            runCurrent()

            viewModel.onEvent(QrScannerEvent.Scanned("https://t.me/ada"))
            runCurrent()
            assertEquals(ScanResult.NotTelegramLink("hello"), viewModel.uiState.value.result)

            viewModel.onEvent(QrScannerEvent.ScanAgain)
            assertTrue(viewModel.uiState.value.isScanning)
        }

    @Test
    fun `linking a device opens only a login code`() =
        runTest {
            links.target = LinkTarget.UserProfile(USER)
            val viewModel = viewModel(ScanPurpose.DeviceLogin).started()

            viewModel.effects.test {
                viewModel.onEvent(QrScannerEvent.Scanned("https://t.me/ada"))
                runCurrent()
                assertEquals(QrScannerEffect.Found, awaitItem())
                assertEquals(ScanResult.NotLoginCode, viewModel.uiState.value.result)

                links.target = LinkTarget.QrCodeLogin(LOGIN)
                viewModel.onEvent(QrScannerEvent.ScanAgain)
                viewModel.onEvent(QrScannerEvent.Scanned(LOGIN))
                runCurrent()
                assertEquals(QrScannerEffect.Found, awaitItem())
                assertEquals(QrScannerEffect.Open(LinkTarget.QrCodeLogin(LOGIN)), awaitItem())
            }
        }

    @Test
    fun `login codes, links to nothing and links Pingogram does not open stay in the scanner`() =
        runTest {
            val viewModel = viewModel().started()
            listOf(
                LinkTarget.LoginCode to ScanResult.LoginCode,
                LinkTarget.NotFound(LinkSubject.Username) to ScanResult.NotFound,
                LinkTarget.Unsupported("tg://proxy") to ScanResult.Unsupported,
            ).forEach { (target, result) ->
                links.target = target
                viewModel.onEvent(QrScannerEvent.Scanned("tg://x"))
                runCurrent()
                assertEquals(result, viewModel.uiState.value.result)
                viewModel.onEvent(QrScannerEvent.ScanAgain)
            }
        }

    @Test
    fun `no network or a flood wait say so`() =
        runTest {
            links.failure = DomainError.FloodWait(30)
            val viewModel = viewModel().started()

            viewModel.onEvent(QrScannerEvent.Scanned("https://t.me/ada"))
            runCurrent()

            assertEquals(ScanResult.Failed(DomainError.FloodWait(30)), viewModel.uiState.value.result)
        }

    @Test
    fun `an image is read for its code`() =
        runTest {
            images.text = "https://t.me/ada"
            links.target = LinkTarget.UserProfile(USER)
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onEvent(QrScannerEvent.PickImage)
                assertEquals(QrScannerEffect.PickImage, awaitItem())
                viewModel.onEvent(QrScannerEvent.ImagePicked("content://picked/1"))
                runCurrent()
                assertEquals(QrScannerEffect.Found, awaitItem())
                assertEquals(QrScannerEffect.Open(LinkTarget.UserProfile(USER)), awaitItem())
            }
            assertEquals(listOf("content://picked/1"), images.read)
        }

    @Test
    fun `an image without a code, or one that cannot be read, says so`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onEvent(QrScannerEvent.ImagePicked("content://picked/1"))
            runCurrent()
            assertEquals(ScanResult.NoCodeInImage, viewModel.uiState.value.result)

            images.failure = IOException("gone")
            viewModel.onEvent(QrScannerEvent.ImagePicked("content://picked/2"))
            runCurrent()
            assertEquals(ScanResult.ImageUnreadable, viewModel.uiState.value.result)

            viewModel.onEvent(QrScannerEvent.ImagePicked(null))
            assertEquals(listOf("content://picked/1", "content://picked/2"), images.read)
        }

    @Test
    fun `the torch follows the switch, and goes off when the screen starts again`() {
        val viewModel = viewModel().started()

        viewModel.onEvent(QrScannerEvent.SetTorch(true))
        assertTrue(viewModel.uiState.value.torchOn)

        viewModel.started()
        assertFalse(viewModel.uiState.value.torchOn)
    }

    private class FakeLinkRepository : LinkRepository {
        var target: LinkTarget = LinkTarget.Search
        var failure: DomainError? = null
        val resolved = mutableListOf<String>()

        override suspend fun resolve(link: String): Outcome<LinkTarget> {
            resolved += link
            return failure?.let { Outcome.Failure(it) } ?: Outcome.Success(target)
        }

        override suspend fun prefillDraft(
            chatId: Long,
            text: String,
        ): Outcome<Unit> = error("Not used by the scanner")
    }

    private class FakeImageReader : QrImageReader {
        var text: String? = null
        var failure: IOException? = null
        val read = mutableListOf<String>()

        override suspend fun read(uri: String): String? {
            read += uri
            failure?.let { throw it }
            return text
        }
    }

    private companion object {
        const val USER = 42L
        const val LOGIN = "tg://login?token=abc"
    }
}
