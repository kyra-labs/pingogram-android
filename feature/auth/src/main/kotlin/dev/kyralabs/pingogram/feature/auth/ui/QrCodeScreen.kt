package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview

/**
 * FR-A6: QR code login. Another logged-in device scans the link; TDLib renews it every few seconds. For now the link
 * is shown as text in the Expressive QR frame (`MaterialShapes.Cookie12Sided`, `docs/DESIGN_SYSTEM.md` §4); drawing
 * the QR code itself comes with the screen polish.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QrCodeScreen(
    state: AuthStepUiState.QrCode,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frameDescription = stringResource(R.string.auth_qr_code_description)
    AuthStepLayout(
        title = stringResource(R.string.auth_qr_title),
        description = stringResource(R.string.auth_qr_instructions),
        modifier = modifier,
        actions = {
            AuthTextButton(
                text = stringResource(R.string.auth_qr_use_phone),
                onClick = { onEvent(AuthEvent.CancelQrCode) },
                inProgress = pendingAction == AuthAction.CancelQrCode,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        },
    ) {
        val frame = MaterialShapes.Cookie12Sided.toShape()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(QR_WIDTH_FRACTION)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, frame)
                    .semantics { contentDescription = frameDescription },
        )
        SelectionContainer {
            Text(
                text = state.link,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
            )
        }
    }
}

private const val QR_WIDTH_FRACTION = 0.7f

@PingogramPreviews
@Composable
private fun QrCodeScreenPreview() {
    AuthScreenPreview(AuthPreviewData.state(AuthStepUiState.QrCode("tg://login?token=AQIDBAUGBwgJCgsMDQ4PEBESExQ")))
}
