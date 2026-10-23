package dev.kyralabs.pingogram.feature.scanner

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The QR code scanner's destination (`QrScannerRoute` of `:core:navigation`): hoists [QrScannerViewModel], asks Android
 * for the camera after the rationale, opens the photo picker (images only, no permission) and the app's settings,
 * copies a text, and hands a Telegram link's target to [onOpen], which closes the scanner and opens it. Whether the
 * device has a camera and Pingogram may use it is read whenever the screen starts (back from the settings). [onBack]
 * closes the scanner.
 */
@Composable
internal fun QrScannerRoute(
    onBack: () -> Unit,
    onOpen: (LinkTarget) -> Unit,
    viewModel: QrScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val currentOnOpen by rememberUpdatedState(onOpen)
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val canAskAgain =
                activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            viewModel.onEvent(QrScannerEvent.PermissionResult(granted, canAskAgain))
        }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            viewModel.onEvent(QrScannerEvent.ImagePicked(uri?.toString()))
        }
    LifecycleStartEffect(viewModel, context) {
        viewModel.onEvent(QrScannerEvent.Started(context.hasCamera(), context.hasCameraPermission()))
        onStopOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                QrScannerEffect.RequestPermission -> {
                    permission.launch(Manifest.permission.CAMERA)
                }

                QrScannerEffect.OpenAppSettings -> {
                    context.openAppSettings()
                }

                QrScannerEffect.PickImage -> {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }

                QrScannerEffect.Found -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }

                is QrScannerEffect.Open -> {
                    currentOnOpen(effect.target)
                }

                is QrScannerEffect.CopyText -> {
                    launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, effect.text))) }
                }
            }
        }
    }
    QrScannerScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

/**
 * The QR code scanner (`docs/DESIGN_SYSTEM.md` §6 "QR code scanner"): full bleed on `scrim`, the camera's viewfinder
 * ([ScannerCamera], or [viewfinder] in previews and tests) under a dimmed `mediaOverlay` with a
 * `MaterialShapes.Cookie12Sided` window outlined in `primaryFixed` ([ViewfinderFrame]); a close button and, where the
 * camera has one, the torch (`FilledTonalIconToggleButton` with the shape morph) at the top; at the bottom what to do
 * and "Scan from image" (`FilledTonalButton`), or the result of a code ([ResultCard]). Without the camera, the
 * rationale or why there is none ([CameraRationale]) takes the viewfinder's place. Screen readers hear the screen's
 * purpose as its pane title and results as they come.
 */
@Composable
internal fun QrScannerScreen(
    state: QrScannerUiState,
    onEvent: (QrScannerEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewfinder: (@Composable BoxScope.() -> Unit)? = null,
) {
    var torchAvailable by rememberSaveable { mutableStateOf(false) }
    val paneTitle = stringResource(R.string.scanner_title)
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .semantics { this.paneTitle = paneTitle },
    ) {
        when (val camera = state.camera) {
            CameraAccess.Granted -> {
                if (viewfinder != null) {
                    viewfinder()
                } else if (!LocalInspectionMode.current) {
                    ScannerCamera(
                        scanning = state.isScanning,
                        torchOn = state.torchOn,
                        onFound = { onEvent(QrScannerEvent.Scanned(it)) },
                        onTorchAvailable = { torchAvailable = it },
                        onUnavailable = {
                            onEvent(
                                QrScannerEvent.Started(hasCamera = false, permissionGranted = true),
                            )
                        },
                    )
                }
                ViewfinderFrame(state.purpose)
            }

            is CameraAccess.Rationale, CameraAccess.NoCamera -> {
                CameraRationale(camera, onEvent, Modifier.align(Alignment.Center))
            }

            CameraAccess.Checking -> {}
        }
        ScannerTopBar(
            torchAvailable = torchAvailable && state.camera == CameraAccess.Granted,
            torchOn = state.torchOn,
            onTorch = { onEvent(QrScannerEvent.SetTorch(it)) },
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        ScannerBottom(state, onEvent, Modifier.align(Alignment.BottomCenter))
    }
}

/** Close at the start and the torch at the end, below the status bar, each with its tooltip. */
@Composable
private fun ScannerTopBar(
    torchAvailable: Boolean,
    torchOn: Boolean,
    onTorch: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val close = stringResource(R.string.scanner_close)
        WithTooltip(close) {
            FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_close), contentDescription = close)
            }
        }
        if (torchAvailable) {
            val torch = stringResource(R.string.scanner_torch)
            WithTooltip(torch) {
                FilledTonalIconToggleButton(
                    checked = torchOn,
                    onCheckedChange = onTorch,
                    shapes = IconButtonDefaults.toggleableShapes(),
                ) {
                    Icon(
                        painter =
                            painterResource(if (torchOn) R.drawable.ic_flashlight_on else R.drawable.ic_flashlight_off),
                        contentDescription = torch,
                    )
                }
            }
        }
    }
}

/** [content] with a plain tooltip of [text] below it, as icon-only buttons in toolbars have (`DESIGN_SYSTEM.md` §6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithTooltip(
    text: String,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/**
 * What the bottom shows: the result of a code ([ResultCard]); else, while the camera reads, what to point it at on a
 * `mediaOverlay` pill; and "Scan from image" wherever no result shows.
 */
@Composable
private fun ScannerBottom(
    state: QrScannerUiState,
    onEvent: (QrScannerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state.result,
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(MaterialTheme.spacing.large),
        transitionSpec = {
            (fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() })
        },
        contentAlignment = Alignment.BottomCenter,
        label = "scanResult",
    ) { result ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            if (result != null) {
                ResultCard(result, onEvent)
            } else {
                if (state.camera == CameraAccess.Granted) Hint(state.purpose)
                FilledTonalButton(
                    onClick = { onEvent(QrScannerEvent.PickImage) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_photo_library),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Text(
                        text = stringResource(R.string.scanner_from_image),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** What to point the camera at, in `onMediaOverlay` on a `mediaOverlay` pill. */
@Composable
private fun Hint(purpose: ScanPurpose) {
    val colors = MaterialTheme.pingogramColors
    Text(
        text =
            stringResource(
                if (purpose == ScanPurpose.DeviceLogin) R.string.scanner_hint_device else R.string.scanner_hint_any,
            ),
        modifier =
            Modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .background(colors.mediaOverlay, MaterialTheme.shapes.extraLarge)
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small),
        color = colors.onMediaOverlay,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The viewfinder's window: `mediaOverlay` dims everything but a `MaterialShapes.Cookie12Sided` window in the middle
 * (the QR login's frame), outlined in `primaryFixed`, which keeps its tone over any picture. Screen readers hear what
 * to point the camera at.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoxScope.ViewfinderFrame(purpose: ScanPurpose) {
    val description =
        stringResource(
            if (purpose ==
                ScanPurpose.DeviceLogin
            ) {
                R.string.scanner_hint_device
            } else {
                R.string.scanner_hint_any
            },
        )
    val window =
        ScannerWindow(
            shape = MaterialShapes.Cookie12Sided.toShape(),
            dim = MaterialTheme.pingogramColors.mediaOverlay,
            outline = MaterialTheme.colorScheme.primaryFixed,
            outlineWidth = MaterialTheme.sizes.keyImageOutline,
            maxSize = MaterialTheme.sizes.illustrationMaxSize,
        )
    Box(
        Modifier
            .matchParentSize()
            .scannerWindow(window)
            .semantics { contentDescription = description },
    )
}

/** Whether the device has a camera of any kind (back, front or external). */
private fun Context.hasCamera(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** The app's page in Android's settings, where the camera can be allowed; nothing where the device has none. */
private fun Context.openAppSettings() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    } catch (_: ActivityNotFoundException) {
        // A device without that page: there is nothing else to open.
    }
}
