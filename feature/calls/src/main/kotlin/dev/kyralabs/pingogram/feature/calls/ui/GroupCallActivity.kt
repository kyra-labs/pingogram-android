package dev.kyralabs.pingogram.feature.calls.ui

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingPolicy
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.feature.calls.engine.ScreenCaptureConsent
import dev.kyralabs.pingogram.feature.calls.service.CallActionReceiver
import dev.kyralabs.pingogram.feature.calls.ui.invite.InvitePeopleSheet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The voice chat's screen as an activity of its own (`docs/adr/0023-group-voice-chats-and-live-streams.md`), in the
 * call screen's task: it shows nothing of the app's chats. Not exported: it opens from the app (a chat's voice chat
 * bar, its info page) and from the voice chat's notification.
 *
 * It closes itself once the voice chat ended and was shown ([GroupCallUiState.Closed]); back leaves it while the voice
 * chat goes on, which its notification brings back. A live stream goes on in picture-in-picture instead, with "Leave".
 */
@AndroidEntryPoint
@Suppress("TooManyFunctions") // Android's callbacks and the window's: picture-in-picture, settings, screen capture.
class GroupCallActivity : ComponentActivity() {
    @Inject lateinit var appearanceRepository: AppearanceRepository

    @Inject lateinit var powerSaving: PowerSavingPolicy

    @Inject lateinit var screenCaptureConsent: ScreenCaptureConsent

    private val viewModel: GroupCallViewModel by viewModels()

    private var inPictureInPicture by mutableStateOf(false)

    /** Back during a live stream: picture-in-picture, where the device allows it, instead of leaving. */
    private val backToPictureInPicture =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (!enterPictureInPicture()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

    private val video: StreamVideo = { modifier ->
        CallVideoView(name = "Stream", mirror = false, onSink = viewModel::setVideoSink, modifier = modifier)
    }

    /** The user's own camera (`docs/adr/0024-video-in-voice-chats.md`), drawn as the call screen's self-view. */
    private val selfVideo: CallVideo = { slot, mirror, modifier ->
        CallVideoView(name = slot.name, mirror = mirror, onSink = viewModel::setSelfVideoSink, modifier = modifier)
    }

    private val participantVideo: ParticipantVideo = { endpointId, fit, modifier ->
        CallVideoView(
            name = "Participant $endpointId",
            mirror = false,
            onSink = { viewModel.setParticipantVideoSink(endpointId, it) },
            modifier = modifier,
            fit = fit,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, backToPictureInPicture)
        val appearance =
            appearanceRepository.appearance.stateIn(lifecycleScope, SharingStarted.WhileSubscribed(), null)
        val stillAnimations =
            powerSaving.inEffect
                .map { it.stillCalls }
                .stateIn(lifecycleScope, SharingStarted.WhileSubscribed(), false)
        setContent {
            val settings by appearance.collectAsStateWithLifecycle()
            val still by stillAnimations.collectAsStateWithLifecycle()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val permissions =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                    val granted = results[Manifest.permission.RECORD_AUDIO] == true
                    val canAskAgain =
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)
                    viewModel.onEvent(GroupCallEvent.MicrophoneResult(granted, canAskAgain))
                }
            val camera =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    val canAskAgain =
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
                    viewModel.onEvent(GroupCallEvent.CameraResult(granted, canAskAgain))
                }
            val screenCapture = rememberScreenCaptureRequest(screenCaptureConsent, ::onScreenCaptureResult)
            val snackbarHostState = remember { SnackbarHostState() }
            var inviting by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                viewModel.effects.collect {
                    onEffect(it, { camera.launch(Manifest.permission.CAMERA) }, screenCapture, snackbarHostState)
                }
            }
            LaunchedEffect(state) { if (state == GroupCallUiState.Closed) finish() }
            val watching = (state as? GroupCallUiState.InCall)?.let { it.watches() && it.hasControls } == true
            LaunchedEffect(watching) { updatePictureInPicture(watching) }
            settings?.let { appearanceSettings ->
                CallTheme(appearanceSettings, stillAnimations = still) {
                    GroupCallScreen(
                        state = state,
                        onEvent = viewModel::onEvent,
                        onMinimize = ::minimize,
                        onRequestMicrophone = { permissions.launch(CALL_PERMISSIONS) },
                        onOpenSettings = ::openAppSettings,
                        video = video,
                        selfVideo = selfVideo,
                        participantVideo = participantVideo,
                        pictureInPicture = inPictureInPicture,
                        onAddPeople = { inviting = true },
                        snackbarHostState = snackbarHostState,
                    )
                    if (inviting) InviteSheet(state, onClose = { inviting = false })
                }
            }
        }
    }

    /** "Add people" over a conference call it may invite into; [onClose] once it closed, inviting or not. */
    @Composable
    private fun InviteSheet(
        state: GroupCallUiState,
        onClose: () -> Unit,
    ) {
        val inCall = state as? GroupCallUiState.InCall
        if (inCall?.canInvite != true || inPictureInPicture) return
        InvitePeopleSheet(
            excluded = inCall.participantUserIds(),
            onInvite = {
                onClose()
                viewModel.onEvent(GroupCallEvent.Invite(it))
            },
            onDismiss = onClose,
        )
    }

    /** Does what an effect of the view model asks; a result's snackbar shows while the next effects come. */
    private fun onEffect(
        effect: CallEffect,
        requestCamera: () -> Unit,
        screenCapture: () -> Unit,
        snackbarHostState: SnackbarHostState,
    ) {
        when (effect) {
            CallEffect.RequestCamera -> {
                requestCamera()
            }

            CallEffect.RequestScreenCapture -> {
                screenCapture()
            }

            CallEffect.OpenSettings -> {
                openAppSettings()
            }

            CallEffect.OpenGroupCall -> {
                Unit
            }

            is CallEffect.Invited -> {
                lifecycleScope.launch { snackbarHostState.showSnackbar(invitedText(effect)) }
            }

            is CallEffect.InviteFailed -> {
                lifecycleScope.launch {
                    snackbarHostState.showSnackbar(
                        inviteFailedText(effect),
                    )
                }
            }
        }
    }

    private fun invitedText(effect: CallEffect.Invited): String =
        listOfNotNull(
            effect.invited.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.call_invite_sent, it, it) },
            effect.refused.takeIf { it > 0 }?.let {
                resources.getQuantityString(
                    R.plurals.call_invite_refused,
                    it,
                    it,
                )
            },
        ).joinToString(" ")

    private fun inviteFailedText(effect: CallEffect.InviteFailed): String =
        errorMessage(
            resources,
            effect.error,
            showDetails =
                applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )

    private fun onScreenCaptureResult(granted: Boolean) = viewModel.onEvent(GroupCallEvent.ScreenSharingResult(granted))

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    /**
     * Leaving with Home: a live stream, or a voice chat with others' videos, goes on in picture-in-picture (Android 12+
     * enters it by itself).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && watches()) enterPictureInPicture()
    }

    private fun watches() = (viewModel.uiState.value as? GroupCallUiState.InCall)?.watches() == true

    private fun canPictureInPicture() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** The picture-in-picture window: a stream's landscape shape, "Leave" as its action. */
    private fun pictureInPictureParams(streaming: Boolean): PictureInPictureParams {
        val leave =
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_call_end_filled),
                getString(R.string.group_call_leave),
                getString(R.string.group_call_leave),
                CallActionReceiver.pendingIntent(this, CallActionReceiver.ACTION_LEAVE),
            )
        return PictureInPictureParams
            .Builder()
            .setAspectRatio(PICTURE_IN_PICTURE_SHAPE)
            .setActions(listOf(leave))
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(streaming) }
            .build()
    }

    private fun updatePictureInPicture(streaming: Boolean) {
        backToPictureInPicture.isEnabled = streaming && canPictureInPicture()
        if (canPictureInPicture()) setPictureInPictureParams(pictureInPictureParams(streaming))
    }

    /** Enters picture-in-picture; false where the device or the user's settings do not allow it. */
    private fun enterPictureInPicture(): Boolean =
        canPictureInPicture() &&
            try {
                enterPictureInPictureMode(pictureInPictureParams(streaming = true))
            } catch (
                @Suppress("SwallowedException") e: IllegalStateException, // Turned off for the app in the settings.
            ) {
                false
            }

    /** Back to the app while the voice chat goes on; a stream or others' videos keep showing in picture-in-picture. */
    private fun minimize() {
        if (watches() && enterPictureInPicture()) return
        packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity)
        moveTaskToBack(true)
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    companion object {
        /** Live streams are mostly landscape, and a pinned video's tile is 16:9 too. */
        private val PICTURE_IN_PICTURE_SHAPE = Rational(16, 9)

        const val EXTRA_ACTION = "action"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_LINK = "link"
        const val EXTRA_INVITATION_CHAT_ID = "invitation_chat_id"
        const val EXTRA_INVITATION_MESSAGE_ID = "invitation_message_id"

        /** Shows the voice chat the user is in. */
        const val ACTION_SHOW = "dev.kyralabs.pingogram.calls.SHOW_VOICE_CHAT"

        /** Joins [EXTRA_CHAT_ID]'s voice chat. */
        const val ACTION_JOIN = "dev.kyralabs.pingogram.calls.JOIN_VOICE_CHAT"

        /** Starts a voice chat in [EXTRA_CHAT_ID] and joins it. */
        const val ACTION_START = "dev.kyralabs.pingogram.calls.START_VOICE_CHAT"

        /** Joins the conference call of the `t.me/call` link [EXTRA_LINK] (`docs/adr/0025-conference-calls.md`). */
        const val ACTION_JOIN_CONFERENCE = "dev.kyralabs.pingogram.calls.JOIN_CONFERENCE"

        /** Joins the conference call that invitation [EXTRA_INVITATION_MESSAGE_ID] in [EXTRA_INVITATION_CHAT_ID] is to. */
        const val ACTION_JOIN_INVITATION = "dev.kyralabs.pingogram.calls.JOIN_INVITATION"

        /** Joins chat [chatId]'s voice chat ([start]: starts one first) and shows it. */
        fun open(
            context: Context,
            chatId: Long,
            start: Boolean = false,
        ) {
            context.startActivity(
                intent(context, if (start) ACTION_START else ACTION_JOIN).putExtra(EXTRA_CHAT_ID, chatId),
            )
        }

        /** Shows the voice chat or conference call the user is in: a one-to-one call that became one. */
        fun show(context: Context) {
            context.startActivity(intent(context, ACTION_SHOW))
        }

        /** Joins the conference call that the invitation [messageId] in chat [chatId] is to, and shows it. */
        fun openInvitation(
            context: Context,
            chatId: Long,
            messageId: Long,
        ) {
            context.startActivity(
                intent(context, ACTION_JOIN_INVITATION)
                    .putExtra(EXTRA_INVITATION_CHAT_ID, chatId)
                    .putExtra(EXTRA_INVITATION_MESSAGE_ID, messageId),
            )
        }

        /** Joins the conference call of [link] and shows it. */
        fun openConference(
            context: Context,
            link: String,
        ) {
            context.startActivity(intent(context, ACTION_JOIN_CONFERENCE).putExtra(EXTRA_LINK, link))
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
        ) = Intent(context, GroupCallActivity::class.java)
            .setAction(action)
            .putExtra(EXTRA_ACTION, action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
