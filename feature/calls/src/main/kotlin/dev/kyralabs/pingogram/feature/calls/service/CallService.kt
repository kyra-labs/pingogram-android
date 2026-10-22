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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the app running while the user's call rings or goes on (`docs/adr/0017-calls-and-telecom.md`): a foreground
 * service of type `phoneCall`, and `microphone` too once the user talks, whose notification is the call's
 * ([CallNotifications], same id). It stops when the call ends.
 *
 * While the call's sound comes from the earpiece, it holds a proximity wake lock: the screen turns off at the ear, so
 * the cheek touches nothing, and on again away from it.
 */
@AndroidEntryPoint
class CallService : Service() {
    @Inject lateinit var callManager: CallManager

    @Inject lateinit var notifications: CallNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var proximity: PowerManager.WakeLock? = null
    private var microphone = false

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
            callManager.call.first { it == null || it.phase is CallPhase.Ended }
            stop()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        microphone = microphone || intent?.getBooleanExtra(EXTRA_MICROPHONE, false) == true
        // Android asks for startForeground within a few seconds: the notification needs the caller's avatar, which
        // TDLib usually has at once; a placeholder-free notification is never shown.
        scope.launch {
            val call = callManager.call.value
            if (call == null || call.phase is CallPhase.Ended) {
                stop()
                return@launch
            }
            val notification = withTimeoutOrNull(BUILD_TIMEOUT) { notifications.build(call) }
            if (notification == null) {
                stop()
                return@launch
            }
            try {
                ServiceCompat.startForeground(this@CallService, CallNotifications.ID, notification, types())
            } catch (
                @Suppress("TooGenericExceptionCaught") e: RuntimeException, // The platform's refusals, by version.
            ) {
                Log.w(TAG, "Could not run in the foreground: ${e.javaClass.simpleName}")
            }
        }
        return START_NOT_STICKY
    }

    /**
     * `phoneCall` always; `microphone` once the user talks and the permission is granted (Android asks for both). Types
     * start with Android 10, `microphone` with Android 11; before 10 `ServiceCompat` passes none.
     */
    private fun types(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val phoneCall = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        val canRecord =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && microphone && canRecord) {
            phoneCall or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            phoneCall
        }
    }

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

/** At the ear: the call is under way, its sound comes from the earpiece (or not yet anywhere). */
private fun ActiveCall.wantsProximity(): Boolean {
    val talking =
        phase == CallPhase.Connecting || phase == CallPhase.Connected || phase == CallPhase.Reconnecting ||
            (isOutgoing && (phase is CallPhase.Ringing || phase == CallPhase.Requesting))
    val route = audio.route?.type
    return talking && (route == null || route == AudioRouteType.Earpiece)
}
