package dev.kyralabs.pingogram.feature.scanner

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.domain.model.isTelegramLink
import dev.kyralabs.pingogram.core.domain.repository.LinkRepository
import dev.kyralabs.pingogram.core.navigation.QrScannerRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** What the QR code scanner shows (`REQUIREMENTS.md` FR-P5, `docs/DESIGN_SYSTEM.md` §6 "QR code scanner"). */
@Immutable
data class QrScannerUiState(
    val purpose: ScanPurpose,
    val camera: CameraAccess = CameraAccess.Checking,
    val result: ScanResult? = null,
    val torchOn: Boolean = false,
) {
    /** Whether the camera's frames are read: it runs, and no result shows. */
    val isScanning: Boolean get() = camera == CameraAccess.Granted && result == null
}

/** Whether the scanner can use the camera. */
@Immutable
sealed interface CameraAccess {
    /** Not known yet: the screen has not said what Android allows. */
    data object Checking : CameraAccess

    /** The device has no camera: only "Scan from image". */
    data object NoCamera : CameraAccess

    /**
     * Pingogram may not use the camera: the rationale shows before Android asks; once Android no longer asks
     * ([deniedForGood]), it offers the app's settings instead.
     */
    data class Rationale(
        val deniedForGood: Boolean = false,
    ) : CameraAccess

    /** The camera runs, its frames read for QR codes. */
    data object Granted : CameraAccess
}

/** What came of a QR code, shown over the viewfinder until the user scans again; a Telegram link opens instead. */
@Immutable
sealed interface ScanResult {
    /** Telegram is asked what the link is. */
    data object Resolving : ScanResult

    /** The code holds [text], which is no Telegram link: it can be copied. */
    data class NotTelegramLink(
        val text: String,
    ) : ScanResult {
        override fun toString() = "NotTelegramLink(…)"
    }

    /** Scanning to link a device, the code is a Telegram link, but not the QR code of a login. */
    data object NotLoginCode : ScanResult

    /** A login code (`t.me/login/12345`): never used, and not to be shared (`docs/adr/0013-deep-links.md`). */
    data object LoginCode : ScanResult

    /** The link leads to nothing the user can see: nobody has the username, the chat is gone. */
    data object NotFound : ScanResult

    /** A Telegram link of a kind Pingogram does not open. */
    data object Unsupported : ScanResult

    /** The picked image shows no QR code, or one too small or blurred to read. */
    data object NoCodeInImage : ScanResult

    /** The picked image could not be read. */
    data object ImageUnreadable : ScanResult

    /** Telegram could not be asked: [error] (no network, a flood wait). */
    data class Failed(
        val error: DomainError,
    ) : ScanResult
}

/** What the user and the screen tell the scanner, handled by [QrScannerViewModel.onEvent]. */
sealed interface QrScannerEvent {
    /** The screen shows (again): whether the device has a camera and Pingogram may use it. */
    data class Started(
        val hasCamera: Boolean,
        val permissionGranted: Boolean,
    ) : QrScannerEvent

    /** "Continue" on the rationale: Android asks, or its settings open once it no longer does. */
    data object Continue : QrScannerEvent

    /** Android answered the request: [granted], and whether it would ask again ([canAskAgain]). */
    data class PermissionResult(
        val granted: Boolean,
        val canAskAgain: Boolean,
    ) : QrScannerEvent

    /** The camera's frames held a QR code with [text]. */
    data class Scanned(
        val text: String,
    ) : QrScannerEvent {
        override fun toString() = "Scanned(…)"
    }

    /** "Scan from image": the photo picker opens. */
    data object PickImage : QrScannerEvent

    /** The user picked the image at [uri] (null: they picked nothing). */
    data class ImagePicked(
        val uri: String?,
    ) : QrScannerEvent {
        override fun toString() = "ImagePicked(picked=${uri != null})"
    }

    /** "Scan again": the result goes, and the camera reads frames again. */
    data object ScanAgain : QrScannerEvent

    /** "Copy" on a code that is no Telegram link. */
    data object Copy : QrScannerEvent

    data class SetTorch(
        val on: Boolean,
    ) : QrScannerEvent
}

/** One-off side effects of [QrScannerViewModel], handled once by its route. */
sealed interface QrScannerEffect {
    /** Ask Android for the camera. */
    data object RequestPermission : QrScannerEffect

    /** Open the app's settings, where the camera can be allowed. */
    data object OpenAppSettings : QrScannerEffect

    /** Open the photo picker, images only. */
    data object PickImage : QrScannerEffect

    /** A code was read: a `Confirm` haptic. */
    data object Found : QrScannerEffect

    /** The code is a Telegram link to [target]: the scanner closes, and the app opens it (`openLinkTarget`). */
    data class Open(
        val target: LinkTarget,
    ) : QrScannerEffect

    /** Put [text] on the clipboard. */
    data class CopyText(
        val text: String,
    ) : QrScannerEffect {
        override fun toString() = "CopyText(…)"
    }
}

/**
 * The QR code scanner (`REQUIREMENTS.md` FR-P5, `docs/adr/0012-camerax-and-zxing-qr-scanner.md`), for [purpose]:
 *
 * - **Camera**: the rationale comes before Android's request ([QrScannerEvent.Continue]); a refusal for good offers the
 *   app's settings, and the screen's next start finds what was allowed there. Without a camera, only an image scans.
 * - **Codes**: the first text the camera or a picked image yields is looked at once: no Telegram link
 *   (`isTelegramLink`) says so and can be copied; a Telegram link is resolved with [LinkRepository.resolve] (it only
 *   reads), and a target the app opens leaves through [QrScannerEffect.Open], where the screen it opens asks before
 *   anything changes (a join, a login). Scanning to link a device ([ScanPurpose.DeviceLogin]) opens only a login code
 *   ([LinkTarget.QrCodeLogin]). What stays shows until "Scan again".
 *
 * Nothing the camera sees is kept or sent: only the text of a code, and only a Telegram link reaches TDLib. Whether
 * Android refused the camera for good is kept in the [SavedStateHandle].
 */
@HiltViewModel
class QrScannerViewModel internal constructor(
    purpose: ScanPurpose,
    private val savedStateHandle: SavedStateHandle,
    private val linkRepository: LinkRepository,
    private val imageReader: QrImageReader,
) : ViewModel() {
    @Inject
    internal constructor(
        savedStateHandle: SavedStateHandle,
        linkRepository: LinkRepository,
        imageReader: QrImageReader,
    ) : this(savedStateHandle.purpose(), savedStateHandle, linkRepository, imageReader)

    private val mutableUiState = MutableStateFlow(QrScannerUiState(purpose))
    val uiState: StateFlow<QrScannerUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<QrScannerEffect>(Channel.BUFFERED)
    val effects: Flow<QrScannerEffect> = effectChannel.receiveAsFlow()

    private var reading: Job? = null

    fun onEvent(event: QrScannerEvent) {
        when (event) {
            is QrScannerEvent.Started -> start(event.hasCamera, event.permissionGranted)
            QrScannerEvent.Continue -> continueToPermission()
            is QrScannerEvent.PermissionResult -> permissionResult(event.granted, event.canAskAgain)
            is QrScannerEvent.Scanned -> read(event.text, fromCamera = true)
            QrScannerEvent.PickImage -> effectChannel.trySend(QrScannerEffect.PickImage)
            is QrScannerEvent.ImagePicked -> event.uri?.let(::readImage)
            QrScannerEvent.ScanAgain -> scanAgain()
            QrScannerEvent.Copy -> copy()
            is QrScannerEvent.SetTorch -> mutableUiState.update { it.copy(torchOn = event.on) }
        }
    }

    private fun start(
        hasCamera: Boolean,
        granted: Boolean,
    ) {
        val camera =
            when {
                !hasCamera -> CameraAccess.NoCamera
                granted -> CameraAccess.Granted
                else -> CameraAccess.Rationale(deniedForGood = savedStateHandle[DENIED_FOR_GOOD] ?: false)
            }
        if (granted) savedStateHandle[DENIED_FOR_GOOD] = false
        // The torch goes off with the camera, in the background too.
        mutableUiState.update { it.copy(camera = camera, torchOn = false) }
    }

    private fun continueToPermission() {
        val access = mutableUiState.value.camera as? CameraAccess.Rationale ?: return
        val effect = if (access.deniedForGood) QrScannerEffect.OpenAppSettings else QrScannerEffect.RequestPermission
        effectChannel.trySend(effect)
    }

    private fun permissionResult(
        granted: Boolean,
        canAskAgain: Boolean,
    ) {
        savedStateHandle[DENIED_FOR_GOOD] = !granted && !canAskAgain
        val camera = if (granted) CameraAccess.Granted else CameraAccess.Rationale(deniedForGood = !canAskAgain)
        mutableUiState.update { it.copy(camera = camera) }
    }

    private fun readImage(uri: String) {
        reading?.cancel()
        mutableUiState.update { it.copy(result = ScanResult.Resolving) }
        reading =
            viewModelScope.launch {
                val text =
                    try {
                        imageReader.read(uri)
                    } catch (_: IOException) {
                        return@launch showResult(ScanResult.ImageUnreadable)
                    } catch (_: SecurityException) {
                        // The picker's grant ended before the image was read.
                        return@launch showResult(ScanResult.ImageUnreadable)
                    }
                if (text == null) showResult(ScanResult.NoCodeInImage) else resolve(text)
            }
    }

    /** The text of a code from the camera, unless a result already shows (a frame read twice). */
    private fun read(
        text: String,
        fromCamera: Boolean,
    ) {
        if (fromCamera && mutableUiState.value.result != null) return
        reading?.cancel()
        mutableUiState.update { it.copy(result = ScanResult.Resolving) }
        reading = viewModelScope.launch { resolve(text) }
    }

    private suspend fun resolve(text: String) {
        effectChannel.send(QrScannerEffect.Found)
        val link = text.trim()
        if (!isTelegramLink(link)) return showResult(ScanResult.NotTelegramLink(text))
        val target =
            when (val resolved = linkRepository.resolve(link)) {
                is Outcome.Success -> resolved.value
                is Outcome.Failure -> return showResult(ScanResult.Failed(resolved.error))
            }
        val loginOnly = mutableUiState.value.purpose == ScanPurpose.DeviceLogin
        when {
            loginOnly && target !is LinkTarget.QrCodeLogin -> showResult(ScanResult.NotLoginCode)
            target == LinkTarget.LoginCode -> showResult(ScanResult.LoginCode)
            target is LinkTarget.NotFound -> showResult(ScanResult.NotFound)
            target is LinkTarget.Unsupported -> showResult(ScanResult.Unsupported)
            else -> effectChannel.send(QrScannerEffect.Open(target))
        }
    }

    private fun showResult(result: ScanResult) {
        mutableUiState.update { it.copy(result = result) }
    }

    private fun scanAgain() {
        reading?.cancel()
        reading = null
        mutableUiState.update { it.copy(result = null) }
    }

    private fun copy() {
        val result = mutableUiState.value.result as? ScanResult.NotTelegramLink ?: return
        effectChannel.trySend(QrScannerEffect.CopyText(result.text))
    }
}

/** The purpose of the route in [this][SavedStateHandle]; [ScanPurpose.Any] for one this version does not know. */
private fun SavedStateHandle.purpose(): ScanPurpose {
    val name = toRoute<QrScannerRoute>().purpose
    return ScanPurpose.entries.firstOrNull { it.name == name } ?: ScanPurpose.Any
}

/** Whether Android refused the camera for good, so "Continue" opens the app's settings. */
private const val DENIED_FOR_GOOD = "scanner.deniedForGood"
