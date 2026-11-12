package dev.kyralabs.pingogram.feature.media.storypost

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.file.cameraFileUri
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.SharedAxisDistance
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXEnter
import dev.kyralabs.pingogram.core.ui.theme.sharedAxisXExit
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.coroutines.launch

/**
 * Posting a new story, full screen: hoists its [NewStoryViewModel] and shows its step, picking the photos and videos
 * ([NewStoryScreen]) or composing the story ([StoryComposeScreen]), whose back returns to picking. Opens Android's
 * photo picker (as the screen first shows, and for "Add more") and the camera app, shows what failed in a snackbar, and
 * leaves through [onClose], also once the stories are posted. Neither the picker nor the camera app needs a
 * permission.
 */
@Composable
internal fun NewStoryRoute(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewStoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val launchers = rememberStoryLaunchers(viewModel::onEvent)
    StoryEffects(viewModel, launchers, snackbarHostState, onClose)
    // The two steps move on the shared axis X (Q-18): composing comes in from the end, picking again from the start.
    val motionScheme = MaterialTheme.motionScheme
    val distance = with(LocalDensity.current) { SharedAxisDistance.roundToPx() }
    AnimatedContent(
        targetState = state.step,
        modifier = modifier,
        transitionSpec = {
            val forward = targetState == NewStoryStep.Compose
            sharedAxisXEnter(motionScheme, distance, forward) togetherWith
                sharedAxisXExit(motionScheme, distance, forward) using
                SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() }
        },
        label = "newStoryStep",
    ) { step ->
        when (step) {
            NewStoryStep.Pick -> {
                NewStoryScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    hasCamera = launchers.hasCamera,
                    snackbarHostState = snackbarHostState,
                )
            }

            NewStoryStep.Compose -> {
                val compose by viewModel.composer.state.collectAsStateWithLifecycle()
                // Only while it is the step shown: the step leaving on its slide takes no back of its own.
                BackHandler(enabled = state.step == NewStoryStep.Compose) {
                    viewModel.onEvent(NewStoryEvent.Back)
                }
                StoryComposeScreen(
                    state = state,
                    compose = compose,
                    caption = viewModel.composer.caption,
                    onEvent = viewModel::onEvent,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}

/**
 * Handles [viewModel]'s effects: closing ([onClose], also once posted), the photo picker and the camera app through
 * [launchers], and what failed as snackbars on [snackbarHostState].
 */
@Composable
private fun StoryEffects(
    viewModel: NewStoryViewModel,
    launchers: StoryLaunchers,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
) {
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(viewModel, launchers) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    NewStoryEffect.Close, NewStoryEffect.Posted -> {
                        return@collect currentOnClose()
                    }

                    is NewStoryEffect.OpenPicker -> {
                        if (launchers.pick(effect.max)) return@collect
                        // Nothing picked and no picker: there is nothing to post.
                        if (viewModel.uiState.value.items
                                .isEmpty()
                        ) {
                            return@collect currentOnClose()
                        }
                        resources.getString(R.string.new_story_no_picker)
                    }

                    is NewStoryEffect.LaunchCamera -> {
                        if (launchers.takePhoto(effect.path)) return@collect
                        viewModel.onEvent(NewStoryEvent.PhotoTaken(taken = false))
                        resources.getString(R.string.new_story_no_camera_app)
                    }

                    NewStoryEffect.NotPostable -> {
                        resources.getString(R.string.new_story_not_postable)
                    }

                    is NewStoryEffect.Failed -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }
                }
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
}

/** Android's photo picker and the camera app, whose results go to [onEvent]. */
private class StoryLaunchers(
    private val context: Context,
    private val picker: ActivityResultLauncher<Int>,
    private val camera: ActivityResultLauncher<Uri>,
) {
    val hasCamera: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    /** Opens the photo picker for at most [max] items; false when no app opens. */
    fun pick(max: Int): Boolean = launching { picker.launch(max) }

    /** Opens the camera app to take a photo into the camera file at [path]; false when no camera app opens. */
    fun takePhoto(path: String): Boolean {
        val uri = context.cameraFileUri(path) ?: return false
        return launching { camera.launch(uri) }
    }

    private inline fun launching(launch: () -> Unit): Boolean =
        try {
            launch()
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}

@Composable
private fun rememberStoryLaunchers(onEvent: (NewStoryEvent) -> Unit): StoryLaunchers {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val picker =
        rememberLauncherForActivityResult(PickStoryMedia()) { uris ->
            currentOnEvent(NewStoryEvent.Picked(uris.map(Uri::toString)))
        }
    val camera =
        rememberLauncherForActivityResult(TakePicture()) { taken -> currentOnEvent(NewStoryEvent.PhotoTaken(taken)) }
    val context = LocalContext.current
    return remember(context, picker, camera) { StoryLaunchers(context, picker, camera) }
}

/**
 * Android's photo picker for photos and videos, at most the input's number of them: the multiple picker for more than
 * one (it takes no fewer than two), the single one otherwise. Its result is the picked items in their order, none when
 * cancelled.
 */
internal class PickStoryMedia : ActivityResultContract<Int, List<Uri>>() {
    override fun createIntent(
        context: Context,
        input: Int,
    ): Intent {
        val request = PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)
        return if (input > 1) {
            PickMultipleVisualMedia(input).createIntent(context, request)
        } else {
            PickVisualMedia().createIntent(context, request)
        }
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): List<Uri> = PickMultipleVisualMedia(MAX_STORY_ITEMS).parseResult(resultCode, intent)
}
