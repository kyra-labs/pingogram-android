package dev.kyralabs.pingogram.feature.media.pip

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.R
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A viewer's video in picture-in-picture (`docs/DESIGN_SYSTEM.md` §6, media viewer): an activity of its own, in a task
 * of its own, so the app's own activity never enters picture-in-picture or resizes with it. The viewer hands the video
 * over ([PictureInPictureVideos]) and closes; this activity enters picture-in-picture at once, showing only the video,
 * with play or pause as the window's action ([PipActionReceiver]). Closing the window stops the video; expanding it
 * gives the video back to its viewer, which the app opens again at that position, and this activity leaves.
 */
@AndroidEntryPoint
class VideoPipActivity : ComponentActivity() {
    @Inject lateinit var videos: PictureInPictureVideos

    private val viewModel: VideoPipViewModel by viewModels()

    /** Whether it asked for picture-in-picture yet; it does so once, when first resumed. */
    private var entered = false

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entered = savedInstanceState?.getBoolean(KEY_ENTERED) == true
        lifecycleScope.launch { videos.actions.collect(viewModel::onAction) }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(state == null) { if (state == null) finish() }
            LaunchedEffect(state?.isPlaying) { state?.let { setPictureInPictureParams(params(it)) } }
            val description = stringResource(R.string.media_pip_video)
            PingogramTheme(themeMode = ThemeMode.Dark) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim)
                        .semantics { contentDescription = description },
                ) {
                    PlayerSurface(
                        player = viewModel.surfacePlayer,
                        modifier = Modifier.fillMaxSize(),
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (entered) return
        entered = true
        val state = viewModel.state.value
        val canEnter = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (state == null || !canEnter || !enter(params(state))) finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ENTERED, entered)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) return
        // Closed, the window leaves the activity stopped; expanded, it comes back started.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.giveBack()
            packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
        }
        finish()
    }

    /** Enters picture-in-picture; false where the user turned it off for the app. */
    private fun enter(params: PictureInPictureParams): Boolean =
        try {
            enterPictureInPictureMode(params)
        } catch (
            @Suppress("SwallowedException") e: IllegalStateException, // Turned off for the app in the settings.
        ) {
            false
        }

    private fun params(state: PipState): PictureInPictureParams {
        val playing = state.isPlaying
        val label = getString(if (playing) R.string.media_pause else R.string.media_play)
        val action =
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (playing) UiR.drawable.ic_pause_filled else UiR.drawable.ic_play_arrow_filled,
                ),
                label,
                label,
                PipActionReceiver.pendingIntent(this),
            )
        return PictureInPictureParams
            .Builder()
            .setAspectRatio(aspectRatio(state.video.width, state.video.height).let { (w, h) -> Rational(w, h) })
            .setActions(listOf(action))
            .build()
    }

    companion object {
        private const val KEY_ENTERED = "entered"

        /** Picture-in-picture windows take aspect ratios from 1 : 2.39 to 2.39 : 1. */
        private const val MAX_ASPECT_THOUSANDTHS = 2390
        private const val THOUSAND = 1000

        /** Plays the video [PictureInPictureVideos] was handed in picture-in-picture. */
        fun open(context: Context) {
            context.startActivity(
                Intent(context, VideoPipActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        /** [width] : [height] within what picture-in-picture allows; 16 : 9 where they are unknown. */
        internal fun aspectRatio(
            width: Int,
            height: Int,
        ): Pair<Int, Int> {
            if (width <= 0 || height <= 0) return WIDE
            val ratio = width * THOUSAND / height
            return when {
                ratio > MAX_ASPECT_THOUSANDTHS -> MAX_ASPECT_THOUSANDTHS to THOUSAND
                ratio < THOUSAND * THOUSAND / MAX_ASPECT_THOUSANDTHS -> THOUSAND to MAX_ASPECT_THOUSANDTHS
                else -> width to height
            }
        }

        private val WIDE = 16 to 9
    }
}

/** The picture-in-picture window's play or pause button, for [VideoPipActivity]. */
class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_PLAY_PAUSE) return
        val videos =
            dagger.hilt.android.EntryPointAccessors
                .fromApplication(context.applicationContext, PipEntryPoint::class.java)
                .videos()
        videos.onAction(PipAction.PlayPause)
    }

    internal companion object {
        const val ACTION_PLAY_PAUSE = "dev.kyralabs.pingogram.media.PIP_PLAY_PAUSE"

        fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                ACTION_PLAY_PAUSE.hashCode(),
                Intent(context, PipActionReceiver::class.java).setAction(ACTION_PLAY_PAUSE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

/** [PictureInPictureVideos] for what Hilt does not inject: the receiver and the app's navigation. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PipEntryPoint {
    fun videos(): PictureInPictureVideos
}
