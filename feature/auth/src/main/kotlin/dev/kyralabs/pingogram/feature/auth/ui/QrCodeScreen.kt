package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.QrCode
import dev.kyralabs.pingogram.feature.auth.ui.components.isCompactHeightWindow
import dev.kyralabs.pingogram.feature.auth.ui.components.squareAroundContent
import dev.kyralabs.pingogram.feature.auth.ui.components.squareOfWidth
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
        secondaryAction = {
            AuthTextButton(
                text = stringResource(R.string.auth_qr_use_phone),
                onClick = { onEvent(AuthEvent.CancelQrCode) },
                inProgress = pendingAction == AuthAction.CancelQrCode,
            )
        },
        explanation = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                QrInstruction(number = 1, text = stringResource(R.string.auth_qr_step_open))
                QrInstruction(number = 2, text = stringResource(R.string.auth_qr_step_settings))
                QrInstruction(number = 3, text = stringResource(R.string.auth_qr_step_confirm))
            }
        },
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .squareOfWidth(QR_FRAME_WIDTH_FRACTION, frameMaxSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie12Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            QrCode(
                link = state.link,
                contentDescription = stringResource(R.string.auth_qr_code_description),
                modifier = Modifier.fillMaxSize(QR_CODE_FRACTION).clip(MaterialTheme.shapes.medium),
            )
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
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
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

private const val QR_FRAME_WIDTH_FRACTION = 0.8f
private const val COMPACT_HEIGHT_SCALE = 0.7f

// The largest rounded square inside the cookie's scallops: its inner radius is 80% of the outer one.
private const val QR_CODE_FRACTION = 0.6f

@PingogramPreviews
@Composable
private fun QrCodeScreenPreview() {
    AuthScreenPreview(AuthPreviewData.qrCode)
}
