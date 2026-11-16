package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.qr.QrCode
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.isCompactHeightWindow
import dev.kyralabs.pingogram.feature.auth.ui.components.squareAroundContent
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * FR-A6: QR code login. Another logged-in device scans the QR code of the `tg://login` link, which TDLib renews every
 * few seconds. The steps Telegram's apps use to scan it explain the headline; the QR code, the step's form, sits in
 * the Expressive QR frame (`MaterialShapes.Cookie12Sided`, `docs/DESIGN_SYSTEM.md` §4), in the end pane on wide
 * windows and in landscape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QrCodeScreen(
    state: AuthStepUiState.QrCode,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // In landscape on a phone the frame would not fit above the action; it shrinks there.
    val frameMaxSize =
        MaterialTheme.sizes.illustrationMaxSize * if (isCompactHeightWindow()) COMPACT_HEIGHT_SCALE else 1f
    AuthStepLayout(
        title = stringResource(R.string.auth_qr_title),
        badgeShape = MaterialShapes.Clover4Leaf,
        badgeIcon = UiR.drawable.ic_photo_camera_filled,
        modifier = modifier,
        description = stringResource(R.string.auth_qr_description),
        secondaryAction = {
            AuthTextButton(
                text = stringResource(R.string.auth_qr_use_phone),
                onClick = { onEvent(AuthEvent.CancelQrCode) },
                inProgress = pendingAction == AuthAction.CancelQrCode,
            )
        },
        explanation = {
            // An ordered list for screen readers (board 23h): "1, Open Telegram on your phone, 1 of 3".
            Column(
                modifier = Modifier.semantics { collectionInfo = CollectionInfo(rowCount = QR_STEPS, columnCount = 1) },
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                QrInstruction(number = 1, text = stringResource(R.string.auth_qr_step_open))
                QrInstruction(number = 2, text = stringResource(R.string.auth_qr_step_settings))
                QrInstruction(number = 3, text = stringResource(R.string.auth_qr_step_confirm))
            }
        },
    ) {
        QrFrame(
            link = state.link,
            maxSize = frameMaxSize,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/**
 * The QR code in its frame (owner decision 2026-11-15, board 23): a `primary` [MaterialShapes.Cookie12Sided], the code
 * plain, dark on light, in its middle, so every scanner reads it, and Pingogram's mark outside the code, on the frame's
 * bottom edge: `onPrimary` on a `primary` `Cookie9Sided` inside a `surface` circle, decorative.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QrFrame(
    link: String,
    maxSize: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier) {
        val side = min(maxWidth * QR_FRAME_WIDTH_FRACTION, maxSize)
        Box(modifier = Modifier.padding(bottom = side * LOGO_OVERHANG)) {
            Box(
                modifier =
                    Modifier
                        .size(side)
                        .background(colors.primary, MaterialShapes.Cookie12Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                QrCode(
                    text = link,
                    contentDescription = stringResource(R.string.auth_qr_code_description),
                    modifier = Modifier.fillMaxSize(QR_CODE_FRACTION).clip(MaterialTheme.shapes.medium),
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = side * LOGO_OVERHANG)
                        .size(side * LOGO_BADGE_FRACTION)
                        .background(colors.surface, CircleShape)
                        .padding(side * LOGO_RING_FRACTION)
                        .background(colors.primary, MaterialShapes.Cookie9Sided.toShape())
                        .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_pingogram),
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.fillMaxSize(LOGO_ICON_FRACTION),
                )
            }
        }
    }
}

/** One numbered step, read by screen readers as one item ("1, Open Telegram on your phone"). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QrInstruction(
    number: Int,
    text: String,
) {
    val locale = LocalConfiguration.current.locales[0]
    val numberText = remember(locale, number) { NumberFormat.getIntegerInstance(locale).format(number) }
    Row(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                collectionItemInfo =
                    CollectionItemInfo(rowIndex = number - 1, rowSpan = 1, columnIndex = 0, columnSpan = 1)
            },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = numberText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialShapes.Cookie6Sided.toShape())
                    .squareAroundContent()
                    .padding(MaterialTheme.spacing.small),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val QR_STEPS = 3
private const val QR_FRAME_WIDTH_FRACTION = 0.8f
private const val COMPACT_HEIGHT_SCALE = 0.7f

// The largest rounded square inside the cookie's scallops: its inner radius is 80% of the outer one.
private const val QR_CODE_FRACTION = 0.6f

// The logo's badge on the frame's bottom edge, as on the board (56 of a 260 frame), its centre on the edge.
private const val LOGO_BADGE_FRACTION = 0.215f
private const val LOGO_OVERHANG = LOGO_BADGE_FRACTION / 2
private const val LOGO_RING_FRACTION = 0.02f
private const val LOGO_ICON_FRACTION = 0.5f

@PingogramPreviews
@Composable
private fun QrCodeScreenPreview() {
    AuthScreenPreview(AuthPreviewData.qrCode)
}
