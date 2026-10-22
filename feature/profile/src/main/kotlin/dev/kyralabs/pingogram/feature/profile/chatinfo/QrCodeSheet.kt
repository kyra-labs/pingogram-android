package dev.kyralabs.pingogram.feature.profile.chatinfo

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The QR code of a group's or channel's link ([url]) in a `ModalBottomSheet` (`docs/DESIGN_SYSTEM.md` §6 "Invite
 * links"): the title in `titleLargeEmphasized`, the code on a `large`-rounded square at most
 * `sizes.illustrationMaxSize` wide, the link below it (selectable, left to right), and a default `Button` "Share link"
 * ([onShare], the system's share sheet). The code is encoded and drawn into a bitmap on a background thread, a pixel
 * per module with its quiet zone, and scaled up without filtering, so it stays sharp at any size; colors keep dark
 * modules on a light ground in every theme ([qrColors]), as scanners expect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrCodeSheet(
    url: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val (moduleColor, groundColor) = qrColors(MaterialTheme.colorScheme)
    val bitmap by produceState<ImageBitmap?>(null, url, moduleColor, groundColor) {
        value = withContext(Dispatchers.Default) { qrBitmap(url, moduleColor, groundColor) }
    }
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            Text(
                text = stringResource(R.string.chat_info_qr_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Box(
                modifier =
                    Modifier
                        .widthIn(max = MaterialTheme.sizes.illustrationMaxSize)
                        .fillMaxWidth(QR_WIDTH_FRACTION)
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.large),
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = stringResource(R.string.chat_info_qr_description),
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = url.removePrefix("https://"),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.numericContent,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onShare, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(R.string.chat_info_share_link),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/**
 * The module and ground colors: `onSurface` on `surfaceContainerLowest` in a light theme, the inverse roles
 * (`inverseOnSurface` on `inverseSurface`) in a dark one, so modules stay dark on a light ground with a contrast well
 * above what scanners need, whatever the theme, contrast level or dynamic palette (as QR login draws its code).
 */
internal fun qrColors(colors: ColorScheme): Pair<Color, Color> =
    if (colors.surfaceContainerLowest.luminance() > colors.onSurface.luminance()) {
        colors.onSurface to colors.surfaceContainerLowest
    } else {
        colors.inverseOnSurface to colors.inverseSurface
    }

/**
 * [text] as a QR code with error correction level M, a pixel per module with [QUIET_ZONE_MODULES] of [ground] around
 * them; null if it does not fit in one (an invite link always does). Runs off the main thread.
 */
internal fun qrBitmap(
    text: String,
    module: Color,
    ground: Color,
): ImageBitmap? {
    val matrix =
        try {
            Encoder.encode(text, ErrorCorrectionLevel.M).matrix
        } catch (_: WriterException) {
            return null
        }
    val side = matrix.width + 2 * QUIET_ZONE_MODULES
    val dark = module.toArgb()
    val light = ground.toArgb()
    val pixels =
        IntArray(side * side) { index ->
            val x = index % side - QUIET_ZONE_MODULES
            val y = index / side - QUIET_ZONE_MODULES
            val inside = x in 0 until matrix.width && y in 0 until matrix.height
            if (inside && matrix.get(x, y).toInt() == 1) dark else light
        }
    return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The quiet zone around the modules, in modules, as the QR code standard requires. */
internal const val QUIET_ZONE_MODULES = 4

/** How much of the sheet's width the code takes on a phone. */
private const val QR_WIDTH_FRACTION = 0.7f
