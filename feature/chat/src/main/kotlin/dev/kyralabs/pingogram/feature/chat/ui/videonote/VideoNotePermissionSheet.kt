package dev.kyralabs.pingogram.feature.chat.ui.videonote

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.VideoNoteEvent
import dev.kyralabs.pingogram.feature.chat.ui.location.PrimaryButton
import dev.kyralabs.pingogram.feature.chat.ui.location.StepActions
import dev.kyralabs.pingogram.feature.chat.ui.voice.CameraRationale
import dev.kyralabs.pingogram.feature.chat.ui.voice.VIDEO_NOTE_PERMISSIONS
import dev.kyralabs.pingogram.feature.chat.ui.voice.has
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Why video messages need the camera and the microphone (`docs/adr/0032-round-video-messages.md`,
 * `docs/DESIGN_SYSTEM.md` §6 Composer), before Android asks, as the Share location sheet's permission step and the QR
 * code scanner explain theirs: a [ModalBottomSheet] with the title in `titleLargeEmphasized`, the camera on
 * `MaterialShapes.Cookie9Sided` in `primaryContainer`, what they are used for in `bodyMedium` `onSurfaceVariant`,
 * "Not now" at the start and "Continue" at the end, which asks Android for both. Once Android no longer asks
 * ([CameraRationale.deniedForGood]), the end button opens the app's settings instead, and coming back with both granted
 * answers for the user. Android's answer goes to [onEvent] ([VideoNoteEvent.PermissionAnswered]); "Not now", the scrim,
 * dragging and back dismiss it ([VideoNoteEvent.DismissRationale]).
 */
@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet
@Composable
internal fun VideoNotePermissionSheet(
    rationale: CameraRationale,
    onEvent: (VideoNoteEvent) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answers ->
            val refused = VIDEO_NOTE_PERMISSIONS.filterNot { answers[it] == true || context.has(it) }
            val canAskAgain =
                activity != null && refused.all { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
            currentOnEvent(VideoNoteEvent.PermissionAnswered(granted = refused.isEmpty(), canAskAgain = canAskAgain))
        }
    // Back from the app's settings with both granted.
    LifecycleResumeEffect(rationale) {
        if (rationale.deniedForGood && VIDEO_NOTE_PERMISSIONS.all(context::has)) {
            currentOnEvent(VideoNoteEvent.PermissionAnswered(granted = true, canAskAgain = true))
        }
        onPauseOrDispose {}
    }
    ModalBottomSheet(
        onDismissRequest = { currentOnEvent(VideoNoteEvent.DismissRationale) },
        sheetState = sheetState,
    ) {
        VideoNotePermissionContent(
            rationale = rationale,
            onNotNow = { currentOnEvent(VideoNoteEvent.DismissRationale) },
            onContinue = {
                if (rationale.deniedForGood) {
                    context.openAppSettings()
                } else {
                    permissions.launch(VIDEO_NOTE_PERMISSIONS)
                }
            },
        )
    }
}

/** The sheet's content, scrolling where it does not fit (landscape, large font scales). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialShapes and RoundedPolygon.toShape
@Composable
internal fun VideoNotePermissionContent(
    rationale: CameraRationale,
    onNotNow: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
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
            text = stringResource(R.string.video_note_permission_title),
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        val badge = MaterialTheme.sizes.headlineBadge
        Box(
            modifier =
                Modifier
                    .size(badge)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_videocam),
                contentDescription = null,
                modifier = Modifier.size(badge / 2),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text =
                stringResource(
                    if (rationale.deniedForGood) {
                        R.string.video_note_permission_denied_body
                    } else {
                        R.string.video_note_permission_body
                    },
                ),
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        StepActions(secondary = R.string.video_note_permission_not_now, onSecondary = onNotNow) {
            PrimaryButton(
                text =
                    if (rationale.deniedForGood) {
                        R.string.video_note_permission_settings
                    } else {
                        R.string.video_note_permission_continue
                    },
                onClick = onContinue,
            )
        }
    }
}

/** The app's page in Android's settings, where its permissions are; nothing where no app shows it. */
private fun Context.openAppSettings() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    } catch (_: ActivityNotFoundException) {
        // No settings app to show it: nothing to open.
    }
}
