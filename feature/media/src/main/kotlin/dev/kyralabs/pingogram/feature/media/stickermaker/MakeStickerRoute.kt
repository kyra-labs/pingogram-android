package dev.kyralabs.pingogram.feature.media.stickermaker

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Making a sticker from a photo, full screen (boards 14a, 14b): hoists its [MakeStickerViewModel], draws the photo
 * through Coil from its TDLib file, and records the editor's drawing into a graphics layer, which "Next" reads back
 * as a bitmap and encodes as a PNG off the main thread ([MakeStickerEffect.Render]). Shares a set's link through
 * Android's share sheet, shows what failed in a snackbar, buzzes `Confirm` once the sticker is in its set, and leaves
 * through [onClose].
 */
@Composable
internal fun MakeStickerRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MakeStickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val capture = rememberGraphicsLayer()
    val snackbarHostState = remember { SnackbarHostState() }
    StickerEffects(viewModel, capture, snackbarHostState, onClose)
    val haptics = LocalHapticFeedback.current
    val done = state.sheet?.step == AddToSetStep.Done
    LaunchedEffect(done) { if (done) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    MakeStickerScreen(
        state = state,
        photo = rememberStickerPhoto(viewModel.fileId),
        capture = capture,
        onEvent = viewModel::onEvent,
        onClose = onClose,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The photo of file [fileId], decoded at [PHOTO_DECODE_PX] at most on its longer side (the sticker is 512 px; zooming
 * in twice stays sharp), into memory the graphics layer can read back (no hardware bitmap).
 */
@Composable
private fun rememberStickerPhoto(fileId: Int): Painter {
    val context = LocalPlatformContext.current
    val request =
        remember(context, fileId) {
            ImageRequest
                .Builder(context)
                .data(TdlibFileImage(fileId))
                .size(PHOTO_DECODE_PX)
                .allowHardware(false)
                .build()
        }
    return rememberAsyncImagePainter(request)
}

/** Handles [viewModel]'s effects: capturing the drawing from [capture], sharing, closing, and snackbars. */
@Composable
private fun StickerEffects(
    viewModel: MakeStickerViewModel,
    capture: GraphicsLayer,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val debuggable = isDebuggable()
    val scope = rememberCoroutineScope()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MakeStickerEffect.Render -> {
                    scope.launch { viewModel.onEvent(MakeStickerEvent.Rendered(capture.drawing())) }
                }

                is MakeStickerEffect.ShareLink -> {
                    if (!context.shareText(effect.link)) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.make_sticker_no_share))
                    }
                }

                MakeStickerEffect.Close -> {
                    currentOnClose()
                }

                is MakeStickerEffect.ShowError -> {
                    snackbarHostState.showSnackbar(errorMessage(resources, effect.error, debuggable))
                }
            }
        }
    }
}

/**
 * The drawing as a PNG: once the frame without the touched layer's frame is drawn, read back from the layer and
 * encoded on [Dispatchers.Default]; null when it cannot be read.
 */
@Suppress("TooGenericExceptionCaught") // The platform's capture throws what its renderer does.
private suspend fun GraphicsLayer.drawing(): ByteArray? =
    try {
        // "Next" touched no layer: the frame that leaves its outline out is drawn before reading.
        withFrameNanos { }
        withFrameNanos { }
        val bitmap = toImageBitmap().asAndroidBitmap()
        withContext(Dispatchers.Default) {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                out.toByteArray()
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

/** Opens Android's share sheet with [text]; false when no app takes it. */
private fun Context.shareText(text: String): Boolean =
    try {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

/** The longest side the photo is decoded at. */
private const val PHOTO_DECODE_PX = 1536

/** PNG ignores the quality; Android asks for one. */
private const val PNG_QUALITY = 100
