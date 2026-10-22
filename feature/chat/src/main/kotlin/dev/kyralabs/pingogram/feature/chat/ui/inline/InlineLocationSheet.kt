package dev.kyralabs.pingogram.feature.chat.ui.inline

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.InlineBotEvent
import dev.kyralabs.pingogram.feature.chat.ui.location.LocationBadge
import dev.kyralabs.pingogram.feature.chat.ui.location.PrimaryButton
import dev.kyralabs.pingogram.feature.chat.ui.location.StepActions
import dev.kyralabs.pingogram.feature.chat.ui.preview.InlinePreviewData
import kotlinx.coroutines.launch

/**
 * The question before the first query to an inline bot that asks for the user's location (`docs/DESIGN_SYSTEM.md` §6
 * "Inline bot location"), in the style of the Share location sheet's permission step: a [ModalBottomSheet] with the
 * question in `titleLargeEmphasized`, the location badge on `MaterialShapes.Cookie9Sided`, what is shared and when in
 * `bodyMedium` `onSurfaceVariant`, "Not now" at the start and "Share location" at the end. Where Android gives
 * Pingogram no location yet ([InlineLocationSheet.needsPermission]), "Share location" asks Android first (both location
 * permissions, so the user may pick an approximate one), then answers whatever Android said: the view model reads the
 * access itself. "Not now", the scrim, dragging and back answer no ([InlineBotEvent.AnswerLocation]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InlineLocationBottomSheet(
    sheet: InlineLocationSheet,
    onEvent: (InlineBotEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val answer = { share: Boolean ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnEvent(InlineBotEvent.AnswerLocation(share)) }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answer(true) }
    ModalBottomSheet(
        onDismissRequest = { currentOnEvent(InlineBotEvent.AnswerLocation(share = false)) },
        sheetState = sheetState,
    ) {
        InlineLocationContent(
            sheet = sheet,
            onNotNow = { answer(false) },
            onShare = { if (sheet.needsPermission) permission.launch(LOCATION_PERMISSIONS) else answer(true) },
        )
    }
}

/** The question's content, scrolling where it does not fit (landscape, large font scales). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InlineLocationContent(
    sheet: InlineLocationSheet,
    onNotNow: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val bot = "@${sheet.username}".bidiIsolated()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.large),
    ) {
        Text(
            text = stringResource(R.string.inline_location_title, bot),
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        LocationBadge(MaterialShapes.Cookie9Sided)
        Text(
            text = stringResource(R.string.inline_location_body, bot),
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        StepActions(secondary = R.string.location_not_now, onSecondary = onNotNow) {
            PrimaryButton(text = R.string.inline_location_share, onClick = onShare)
        }
    }
}

/** Both location permissions: Android 12 and later then let the user pick a precise or an approximate location. */
private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@PingogramPreviews
@Composable
private fun InlineLocationContentPreview() {
    PingogramPreview { InlineLocationContent(InlinePreviewData.locationSheet, onNotNow = {}, onShare = {}) }
}
