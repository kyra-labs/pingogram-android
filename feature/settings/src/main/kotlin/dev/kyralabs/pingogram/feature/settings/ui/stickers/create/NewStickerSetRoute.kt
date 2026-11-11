package dev.kyralabs.pingogram.feature.settings.ui.stickers.create

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Making a sticker set: hoists [NewStickerSetViewModel], opens the system photo picker (images only, no permission) for
 * "Add sticker", shares the published set's link through Android's share sheet, and shows errors as snackbars. [onBack]
 * leaves the page; [onOpenSet] opens the published set's sheet.
 */
@Composable
internal fun NewStickerSetRoute(
    onBack: () -> Unit,
    onOpenSet: (setId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewStickerSetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val emoji by viewModel.emoji.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showDetails = isDebuggable()
    val currentOnBack by rememberUpdatedState(onBack)
    val photoPicker =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            if (uri != null) viewModel.onEvent(NewStickerSetEvent.PhotoPicked(uri.toString()))
        }
    LaunchedEffect(viewModel) {
        var snackbar: Job? = null
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    NewStickerSetEffect.PickPhoto -> {
                        photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        ""
                    }

                    NewStickerSetEffect.Close -> {
                        currentOnBack()
                        ""
                    }

                    NewStickerSetEffect.Published -> {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        ""
                    }

                    is NewStickerSetEffect.Notice -> {
                        resources.getString(effect.text)
                    }

                    is NewStickerSetEffect.ShowError -> {
                        errorMessage(resources, effect.error, showDetails)
                    }
                }
            if (message.isNotEmpty()) {
                snackbar?.cancel()
                snackbar = launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }
    NewStickerSetScreen(
        state = state,
        emoji = emoji,
        onEvent = viewModel::onEvent,
        onShare = { link ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
            try {
                context.startActivity(Intent.createChooser(send, resources.getString(R.string.settings_stickers_share)))
            } catch (_: ActivityNotFoundException) {
                // No app shares text: there is nothing to offer.
            }
        },
        onOpenSet = onOpenSet,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
