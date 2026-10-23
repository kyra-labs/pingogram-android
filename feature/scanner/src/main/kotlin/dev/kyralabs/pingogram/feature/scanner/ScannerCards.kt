package dev.kyralabs.pingogram.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The result of a code on a `surfaceContainerHigh` card with `extraLarge` corners, at most `contentMaxWidth` wide, a
 * polite live region: while Telegram is asked, a `LinearWavyProgressIndicator` under "Opening link…"; otherwise what
 * the code is, the text of one that is no Telegram link (three lines at most) with "Copy", and "Scan again".
 */
@Composable
internal fun ResultCard(
    result: ScanResult,
    onEvent: (QrScannerEvent) -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(MaterialTheme.spacing.large)
                    .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Text(
                text = resultTitle(result),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            resultBody(result)?.let { body ->
                Text(
                    text = body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (result is ScanResult.NotTelegramLink) RESULT_TEXT_MAX_LINES else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    style =
                        if (result is ScanResult.NotTelegramLink) {
                            MaterialTheme.typography.bodyMedium.userContent
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                )
            }
            if (result == ScanResult.Resolving) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            } else {
                ResultActions(canCopy = result is ScanResult.NotTelegramLink, onEvent = onEvent)
            }
        }
    }
}

/** "Copy" (a `TextButton`) at the start where there is a text to copy, "Scan again" (a default `Button`) at the end. */
@Composable
private fun ResultActions(
    canCopy: Boolean,
    onEvent: (QrScannerEvent) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (canCopy) {
            TextButton(onClick = { onEvent(QrScannerEvent.Copy) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.scanner_copy), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Box {}
        }
        Button(onClick = { onEvent(QrScannerEvent.ScanAgain) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.scanner_scan_again), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

@Composable
private fun resultTitle(result: ScanResult): String =
    stringResource(
        when (result) {
            ScanResult.Resolving -> R.string.scanner_resolving
            is ScanResult.NotTelegramLink -> R.string.scanner_not_telegram
            ScanResult.NotLoginCode -> R.string.scanner_not_login
            ScanResult.LoginCode -> R.string.scanner_login_code
            ScanResult.NotFound -> R.string.scanner_not_found
            ScanResult.Unsupported -> R.string.scanner_unsupported
            ScanResult.NoCodeInImage -> R.string.scanner_no_code
            ScanResult.ImageUnreadable -> R.string.scanner_image_unreadable
            is ScanResult.Failed -> R.string.scanner_failed
        },
    )

@Composable
private fun resultBody(result: ScanResult): String? =
    when (result) {
        is ScanResult.NotTelegramLink -> result.text
        ScanResult.NotLoginCode -> stringResource(R.string.scanner_not_login_body)
        ScanResult.LoginCode -> stringResource(R.string.scanner_login_code_body)
        ScanResult.NoCodeInImage -> stringResource(R.string.scanner_no_code_body)
        is ScanResult.Failed -> errorMessage(LocalResources.current, result.error, isDebuggable()).ifEmpty { null }
        else -> null
    }

/**
 * Before Android asks for the camera, and when it cannot be used: on a `surfaceContainerHigh` card, a
 * `MaterialShapes.Cookie9Sided` badge in `primaryContainer`, what the camera is used for ("Pingogram uses the camera
 * only to read QR codes. Nothing the camera sees leaves your phone."), and "Continue" (or "Open settings" once Android
 * no longer asks); without a camera, that there is none. "Scan from image" stays below either way.
 */
@Composable
internal fun CameraRationale(
    camera: CameraAccess,
    onEvent: (QrScannerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noCamera = camera == CameraAccess.NoCamera
    val deniedForGood = (camera as? CameraAccess.Rationale)?.deniedForGood == true
    Surface(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = MaterialTheme.spacing.large)
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            CameraBadge()
            Text(
                text =
                    stringResource(
                        if (noCamera) R.string.scanner_no_camera_title else R.string.scanner_rationale_title,
                    ),
                modifier = Modifier.semantics { heading() },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Text(
                text =
                    stringResource(
                        when {
                            noCamera -> R.string.scanner_no_camera_body
                            deniedForGood -> R.string.scanner_denied_body
                            else -> R.string.scanner_rationale_body
                        },
                    ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!noCamera) {
                Button(onClick = { onEvent(QrScannerEvent.Continue) }, shapes = ButtonDefaults.shapes()) {
                    Text(
                        text =
                            stringResource(
                                if (deniedForGood) R.string.scanner_open_settings else R.string.scanner_continue,
                            ),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        }
    }
}

/** The camera symbol in `onPrimaryContainer` on a `MaterialShapes.Cookie9Sided` badge in `primaryContainer`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CameraBadge() {
    val badgeSize = MaterialTheme.sizes.headlineBadge
    Box(
        Modifier
            .size(badgeSize)
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
        Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_photo_camera),
            contentDescription = null,
            modifier = Modifier.size(badgeSize / 2),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** The lines the text of a code that is no Telegram link may take. */
private const val RESULT_TEXT_MAX_LINES = 3
