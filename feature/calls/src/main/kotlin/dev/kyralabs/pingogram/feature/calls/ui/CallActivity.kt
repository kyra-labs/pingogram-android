package dev.kyralabs.pingogram.feature.calls.ui

import android.Manifest
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.isDark
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.feature.calls.engine.ScreenCaptureConsent
import dev.kyralabs.pingogram.feature.calls.service.CallActionReceiver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The call screen as an activity of its own (`docs/adr/0018-calls-and-telecom.md`): it shows over the lock screen and
 * turns the screen on while a call rings or goes on, and shows nothing of the app's chats, which stay behind the lock
 * screen and the passcode lock. Not exported: it opens from the app (call buttons), from the call's notifications
 * (its full-screen intent, "Answer", a tap) and from Telecom.
 *
 * It closes itself once the call ended and was shown ([CallUiState.Closed]); back leaves it while the call goes on,
 * which the call's notification brings back. A video call goes on in picture-in-picture instead
 * (`docs/adr/0019-video-and-group-calls-scope.md`): when the user leaves it (Home, back, "Back to the app"), its video
 * keeps showing in a small window with "Hang up", which a tap brings back to full screen.
 */
@AndroidEntryPoint
@Suppress("TooManyFunctions") // Android's callbacks and the window's: lock screen, picture-in-picture, settings, chat.
class CallActivity : ComponentActivity() {
    @Inject lateinit var appearanceRepository: AppearanceRepository

    @Inject lateinit var screenCaptureConsent: ScreenCaptureConsent

    private val viewModel: CallViewModel by viewModels()

    private var inPictureInPicture by mutableStateOf(false)

    /** Back during a video call: picture-in-picture, where the device allows it, instead of leaving. */
    private val backToPictureInPicture =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!enterPictureInPicture()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        onBackPressedDispatcher.addCallback(this, backToPictureInPicture)
        val appearance =
            appearanceRepository.appearance.stateIn(lifecycleScope, SharingStarted.WhileSubscribed(), null)
        setContent {
            val settings by appearance.collectAsStateWithLifecycle()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val permissions =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    val granted = results[Manifest.permission.RECORD_AUDIO] == true
                    val canAskAgain =
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.RECORD_AUDIO,
                        )
                    viewModel.onEvent(CallEvent.MicrophoneResult(granted, canAskAgain))
                }
            val camera =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    val canAskAgain =
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
                    viewModel.onEvent(CallEvent.CameraResult(granted, canAskAgain))
                }
            val screenCapture =
                rememberScreenCaptureRequest(screenCaptureConsent, ::onScreenCaptureResult)
            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        CallEffect.RequestCamera -> camera.launch(Manifest.permission.CAMERA)
                        CallEffect.RequestScreenCapture -> screenCapture()
                        CallEffect.OpenSettings -> openAppSettings()
                    }
                }
            }
            LaunchedEffect(state) { if (state == CallUiState.Closed) finish() }
            // A call that ends in picture-in-picture is not rated: its window has no room for the question.
            val asksRating = (state as? CallUiState.InCall)?.rating != null
            LaunchedEffect(asksRating, inPictureInPicture) {
                if (asksRating && inPictureInPicture) viewModel.onEvent(CallEvent.DismissRating)
            }
            val videoShows = (state as? CallUiState.InCall)?.video?.shows == true
            LaunchedEffect(videoShows) { updatePictureInPicture(videoShows) }
            settings?.let { appearanceSettings ->
                CallTheme(appearanceSettings) {
                    CallScreen(
                        state = state,
                        onEvent = viewModel::onEvent,
                        onMinimize = ::minimize,
                        onRequestMicrophone = { permissions.launch(CALL_PERMISSIONS) },
                        onOpenSettings = ::openAppSettings,
                        onMessage = ::declineAndMessage,
                        video = video,
                        pictureInPicture = inPictureInPicture,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // "Answer" from the notification while the screen already shows the ringing call.
        if (intent.action == ACTION_ANSWER) viewModel.onEvent(CallEvent.Answer)
    }

    /** The call's video, drawn by WebRTC's renderer ([CallVideoView]), its frames from the call manager. */
    private val video: CallVideo = { slot, mirror, modifier ->
        CallVideoView(
            name = slot.name,
            mirror = mirror,
            onSink = { sink -> viewModel.setVideoSink(slot, sink) },
            modifier = modifier,
            fit = slot == CallVideoSlot.RemoteScreen,
        )
    }

    /** Over the lock screen, turning the screen on: a call must be answerable without unlocking. */
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION") // The only way on Android 8.0.
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun onScreenCaptureResult(granted: Boolean) = viewModel.onEvent(CallEvent.ScreenSharingResult(granted))

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    /** Leaving with Home: a video call goes on in picture-in-picture (Android 12+ enters it by itself). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && videoShows()) enterPictureInPicture()
    }

    private fun videoShows() = (viewModel.uiState.value as? CallUiState.InCall)?.video?.shows == true

    private fun canPictureInPicture() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * The picture-in-picture window: the video's portrait shape, "Hang up" as its action, and on Android 12+ entered
     * by itself on Home while [videoShows].
     */
    private fun pictureInPictureParams(videoShows: Boolean): PictureInPictureParams {
        val hangUp =
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_call_end_filled),
                getString(R.string.call_hang_up),
                getString(R.string.call_hang_up),
                CallActionReceiver.pendingIntent(this, CallActionReceiver.ACTION_HANG_UP),
            )
        return PictureInPictureParams
            .Builder()
            .setAspectRatio(PICTURE_IN_PICTURE_SHAPE)
            .setSourceRectHint(videoBounds())
            .setActions(listOf(hangUp))
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(videoShows) }
            .build()
    }

    /**
     * Where the video is, for the window's animation into picture-in-picture: it fills the window, so the window's
     * middle in the picture-in-picture shape.
     */
    private fun videoBounds(): Rect {
        val width = window.decorView.width
        val height = window.decorView.height
        val fitted = minOf(height, width * PICTURE_IN_PICTURE_SHAPE.denominator / PICTURE_IN_PICTURE_SHAPE.numerator)
        val top = (height - fitted) / 2
        return Rect(0, top, width, top + fitted)
    }

    private fun updatePictureInPicture(videoShows: Boolean) {
        backToPictureInPicture.isEnabled = videoShows && canPictureInPicture()
        if (canPictureInPicture()) setPictureInPictureParams(pictureInPictureParams(videoShows))
    }

    /** Enters picture-in-picture; false where the device or the user's settings do not allow it. */
    private fun enterPictureInPicture(): Boolean =
        canPictureInPicture() &&
            try {
                enterPictureInPictureMode(pictureInPictureParams(videoShows = true))
            } catch (
                @Suppress("SwallowedException") e: IllegalStateException, // Turned off for the app in the settings.
            ) {
                false
            }

    /**
     * Back to the app while the call goes on: unlocking first if the screen is locked. A video call keeps showing in
     * picture-in-picture.
     */
    private fun minimize() {
        if (videoShows() && enterPictureInPicture()) return
        val open = packageManager.getLaunchIntentForPackage(packageName)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(this, null)
        } else if (open != null) {
            startActivity(open)
        }
        moveTaskToBack(true)
    }

    /**
     * "Message" on an incoming call: declines it and opens the chat with the caller (a private chat's id is its
     * user's), once the screen is unlocked: the chats never show over the lock screen.
     */
    private fun declineAndMessage(userId: Long) {
        viewModel.onEvent(CallEvent.HangUp)
        val chat = NotificationIntents.openChat(this, chatId = userId) ?: return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    // The application's context: this screen closes once the declined call was shown.
                    override fun onDismissSucceeded() = applicationContext.startActivity(chat)
                },
            )
        } else {
            startActivity(chat)
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    companion object {
        /** A phone's portrait video, which most calls send. */
        private val PICTURE_IN_PICTURE_SHAPE = Rational(9, 16)

        const val EXTRA_ACTION = "action"
        const val EXTRA_USER_ID = "user_id"

        /** With [ACTION_START]: call with the camera on. */
        const val EXTRA_VIDEO = "video"

        /** Shows the user's call. */
        const val ACTION_SHOW = "dev.kyralabs.pingogram.calls.SHOW"

        /** Answers the call that rings. */
        const val ACTION_ANSWER = "dev.kyralabs.pingogram.calls.ANSWER"

        /** Calls [EXTRA_USER_ID]. */
        const val ACTION_START = "dev.kyralabs.pingogram.calls.START"

        /**
         * Calls user [userId], [video] with the camera on: the call buttons of the conversation and the profile, and
         * "Call back".
         */
        fun startCall(
            context: Context,
            userId: Long,
            video: Boolean = false,
        ) {
            context.startActivity(
                intent(context, ACTION_START).putExtra(EXTRA_USER_ID, userId).putExtra(EXTRA_VIDEO, video),
            )
        }

        internal fun pendingIntent(
            context: Context,
            action: String,
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                action.hashCode(),
                intent(context, action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun intent(
            context: Context,
            action: String,
        ) = Intent(context, CallActivity::class.java)
            .setAction(action)
            .putExtra(EXTRA_ACTION, action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
