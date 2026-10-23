package dev.kyralabs.pingogram.feature.calls.ui

import android.Manifest
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.isDark
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
 * which the call's notification brings back.
 */
@AndroidEntryPoint
class CallActivity : ComponentActivity() {
    @Inject lateinit var appearanceRepository: AppearanceRepository

    private val viewModel: CallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
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
            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        CallEffect.RequestCamera -> camera.launch(Manifest.permission.CAMERA)
                        CallEffect.OpenSettings -> openAppSettings()
                    }
                }
            }
            LaunchedEffect(state) { if (state == CallUiState.Closed) finish() }
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
        )
    }

    @Composable
    private fun CallTheme(
        settings: AppearanceSettings,
        content: @Composable () -> Unit,
    ) {
        val dark = settings.themeMode.isDark()
        LaunchedEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            )
        }
        PingogramTheme(
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            palette = settings.palette,
            contrast = settings.contrast,
            amoled = settings.amoled,
            content = content,
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

    /** Back to the app while the call goes on: unlocking first if the screen is locked. */
    private fun minimize() {
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
        const val EXTRA_ACTION = "action"
        const val EXTRA_USER_ID = "user_id"

        /** Shows the user's call. */
        const val ACTION_SHOW = "dev.kyralabs.pingogram.calls.SHOW"

        /** Answers the call that rings. */
        const val ACTION_ANSWER = "dev.kyralabs.pingogram.calls.ANSWER"

        /** Calls [EXTRA_USER_ID]. */
        const val ACTION_START = "dev.kyralabs.pingogram.calls.START"

        /** Calls user [userId]: the call buttons of the conversation and the profile, and "Call back". */
        fun startCall(
            context: Context,
            userId: Long,
        ) {
            context.startActivity(intent(context, ACTION_START).putExtra(EXTRA_USER_ID, userId))
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
