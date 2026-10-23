package dev.kyralabs.pingogram.feature.calls.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.feature.calls.ActiveCall
import dev.kyralabs.pingogram.feature.calls.AudioRouteType
import dev.kyralabs.pingogram.feature.calls.CallForeground
import dev.kyralabs.pingogram.feature.calls.CallManager
import dev.kyralabs.pingogram.feature.calls.CallPhase
import dev.kyralabs.pingogram.feature.calls.GroupCallManager
import dev.kyralabs.pingogram.feature.calls.engine.RemoteVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the app running while the user's call rings or goes on, or the user is in a voice chat
 * (`docs/adr/0018-calls-and-telecom.md`, `docs/adr/0023-group-voice-chats-and-live-streams.md`): a foreground
 * service of type `phoneCall`, `microphone` too once the user talks and `camera` while the camera sends
 * (`docs/adr/0019-video-and-group-calls-scope.md`), whose notification is the call's ([CallNotifications], same id). It
 * stops when the call ends.
 *
 * While the call's sound comes from the earpiece and no video shows, it holds a proximity wake lock: the screen turns
 * off at the ear, so the cheek touches nothing, and on again away from it.
 */
@AndroidEntryPoint
class CallService : Service() {
    @Inject lateinit var callManager: CallManager

    @Inject lateinit var groupCallManager: GroupCallManager

    @Inject lateinit var notifications: CallNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var proximity: PowerManager.WakeLock? = null
    private var microphone = false
    private var camera = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            callManager.call
                .map { it?.wantsProximity() == true }
                .distinctUntilChanged()
                .collect(::setProximity)
        }
        scope.launch {
            // Over once neither a call nor a voice chat goes on.
            combine(callManager.call, groupCallManager.call) { call, group ->
                (call == null || call.phase is CallPhase.Ended) && group.isOver()
            }.first { it }
            stop()
        }
        // The camera's type comes and goes with the camera: startForeground again with the types it needs.
        scope.launch {
            callManager.call
                .map { it?.video?.camera != null }
                .distinctUntilChanged()
                .drop(1)
                .collect { on ->
                    camera = on
                    promote()
                }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        microphone = microphone || intent?.getBooleanExtra(EXTRA_MICROPHONE, false) == true
        scope.launch { promote() }
        return START_NOT_STICKY
    }

    /**
     * Runs in the foreground with the call's notification and the types the call needs now. Android asks for it within
     * a few seconds: the notification needs the caller's avatar, which TDLib usually has at once; a placeholder-free
     * notification is never shown.
     */
    private suspend fun promote() {
        val call = callManager.call.value?.takeIf { it.phase !is CallPhase.Ended }
        val group = groupCallManager.call.value?.takeIf { !it.isOver() }
        if (call == null && group == null) {
            stop()
            return
        }
        val notification =
            withTimeoutOrNull(BUILD_TIMEOUT) {
                if (call != null) notifications.build(call) else group?.let { notifications.build(it) }
            }
        if (notification == null) {
            stop()
            return
        }
        try {
            ServiceCompat.startForeground(this, CallNotifications.ID, notification, types())
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException, // The platform's refusals, by version.
        ) {
            Log.w(TAG, "Could not run in the foreground: ${e.javaClass.simpleName}")
        }
    }

    /**
     * `phoneCall` always; `microphone` once the user talks and `camera` while the camera sends, each with its
     * permission granted (Android asks for both). Types start with Android 10, `microphone` and `camera` with
     * Android 11; before 10 `ServiceCompat` passes none.
     */
    private fun types(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (microphone && granted(Manifest.permission.RECORD_AUDIO)) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (camera && granted(Manifest.permission.CAMERA)) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        }
        return types
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    // Acquired and released by separate calls: when the call's audio reaches or leaves the earpiece, and at the end
    // (stop, onDestroy); a call has no set length, so no timeout.
    @SuppressLint("WakelockTimeout", "Wakelock")
    private fun setProximity(on: Boolean) {
        val power = getSystemService(PowerManager::class.java)
        if (on && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = proximity ?: power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKE_LOCK_TAG)
            proximity?.takeUnless { it.isHeld }?.acquire()
        } else {
            proximity?.takeIf { it.isHeld }?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    private fun stop() {
        setProximity(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        setProximity(false)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CallService"
        const val WAKE_LOCK_TAG = "pingogram:call-proximity"
        const val EXTRA_MICROPHONE = "microphone"
        val BUILD_TIMEOUT = 3.seconds
    }

    /** [CallForeground] by this service. */
    @Singleton
    class Starter
        @Inject
        internal constructor(
            @param:ApplicationContext private val context: Context,
        ) : CallForeground {
            override fun start(withMicrophone: Boolean) {
                val intent = Intent(context, CallService::class.java).putExtra(EXTRA_MICROPHONE, withMicrophone)
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: IllegalStateException) {
                    // Android 12+ refuses foreground services started from the background (an incoming call while
                    // the app sleeps): the call's notification, which has a full-screen intent, still rings.
                    Log.w(TAG, "Could not start the call service: ${e.javaClass.simpleName}")
                }
            }

            override fun stop() {
                context.stopService(Intent(context, CallService::class.java))
            }
        }
}

/**
 * At the ear: the call is under way, its sound comes from the earpiece (or not yet anywhere), and no video shows, which
 * is watched, not held to the ear.
 */
private fun ActiveCall.wantsProximity(): Boolean {
    if (video.camera != null || video.remote != RemoteVideo.Off) return false
    val talking =
        phase == CallPhase.Connecting || phase == CallPhase.Connected || phase == CallPhase.Reconnecting ||
            (isOutgoing && (phase is CallPhase.Ringing || phase == CallPhase.Requesting))
    val route = audio.route?.type
    return talking && (route == null || route == AudioRouteType.Earpiece)
}
