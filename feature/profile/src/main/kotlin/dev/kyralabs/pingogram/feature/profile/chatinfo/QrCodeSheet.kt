package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.qr.QrCode
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R

/**
 * The QR code of a group's or channel's link ([url]) in a `ModalBottomSheet` (`docs/DESIGN_SYSTEM.md` §6 "Invite
 * links"): the title in `titleLargeEmphasized`, the code on a `large`-rounded square at most
 * `sizes.illustrationMaxSize` wide, the link below it (selectable, left to right), and a default `Button` "Share link"
 * ([onShare], the system's share sheet). The code is `core:ui`'s [QrCode], dark modules on a light ground in every
 * theme, as scanners expect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrCodeSheet(
    url: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
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
            QrCode(
                text = url,
                contentDescription = stringResource(R.string.chat_info_qr_description),
                modifier =
                    Modifier
                        .widthIn(max = MaterialTheme.sizes.illustrationMaxSize)
                        .fillMaxWidth(QR_WIDTH_FRACTION)
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.large),
            )
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

/** How much of the sheet's width the code takes on a phone. */
private const val QR_WIDTH_FRACTION = 0.7f
