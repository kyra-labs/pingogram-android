package dev.kyralabs.pingogram.feature.calls.system

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.net.toUri
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.feature.calls.AudioRoute
import dev.kyralabs.pingogram.feature.calls.AudioRouteType
import dev.kyralabs.pingogram.feature.calls.CallAudio
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CallSystem] on Android's Telecom through Jetpack's `core-telecom` (`docs/adr/0018-calls-and-telecom.md`): Pingogram
 * registers as a self-managed calling app (`MANAGE_OWN_CALLS`), each call is added with [CallsManager.addCall], and
 * Telecom routes its audio (earpiece, speaker, wired headset, Bluetooth), puts it on hold for a phone call and lets
 * headsets, cars and watches answer and end it. Before Android 14 the library does this through its own
 * `ConnectionService`; from 14 through the platform's call API.
 *
 * A device without Telecom (some tablets and Chromebooks), or a call Telecom refuses, falls back to
 * [AudioManagerCall]: the audio mode and route set directly, earpiece and speaker.
 */
@Singleton
// MANAGE_OWN_CALLS is declared by :app's manifest, the one place that lists permissions (AppManifestTest), and by
// core-telecom's: lint, checking this module alone, sees neither.
@SuppressLint("MissingPermission")
class TelecomCallSystem
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CallSystem {
        private val callsManager: CallsManager? by lazy {
            try {
                CallsManager(context).also { it.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE) }
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "Telecom is not available: ${e.javaClass.simpleName}")
                null
            }
        }

        override fun add(
            scope: CoroutineScope,
            request: SystemCallRequest,
            listener: SystemCallListener,
        ): SystemCall {
            val call = TelecomCall(context)
            scope.launch {
                val manager = callsManager
                if (manager == null) {
                    call.fallBack()
                    return@launch
                }
                try {
                    manager.addCall(
                        attributes(request),
                        onAnswer = { listener.onAnswer() },
                        onDisconnect = { listener.onDisconnect() },
                        onSetActive = { listener.onHold(false) },
                        onSetInactive = { listener.onHold(true) },
                    ) { call.attach(this) }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    // CallException, SecurityException, …: anything.
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    Log.w(TAG, "Telecom refused the call: ${e.javaClass.simpleName}")
                    call.fallBack()
                }
            }
            return call
        }

        private fun attributes(request: SystemCallRequest) =
            CallAttributesCompat(
                displayName = request.displayName,
                address = "pingogram:user/${request.userId}".toUri(),
                direction =
                    if (request.isOutgoing) {
                        CallAttributesCompat.DIRECTION_OUTGOING
                    } else {
                        CallAttributesCompat.DIRECTION_INCOMING
                    },
                callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
                callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
            )

        private companion object {
            const val TAG = "TelecomCallSystem"
        }
    }

/**
 * One call in Telecom. What the app asks of it is queued until Telecom has added the call ([attach]), then carried out
 * in order in its [CallControlScope].
 */
private class TelecomCall(
    private val context: Context,
) : SystemCall {
    private val mutableAudio = MutableStateFlow(CallAudio())
    override val audio: StateFlow<CallAudio> = mutableAudio.asStateFlow()

    private val mutableMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = mutableMuted.asStateFlow()

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var endpoints: Map<String, CallEndpointCompat> = emptyMap()

    @Volatile private var fallback: AudioManagerCall? = null

    private sealed interface Command {
        data object Answer : Command

        data object Active : Command

        data object Inactive : Command

        data class Route(
            val route: AudioRoute,
        ) : Command

        data class End(
            val cause: SystemEndCause,
        ) : Command
    }

    fun attach(scope: CallControlScope) {
        val audioJob =
            scope.launch {
                combine(scope.currentCallEndpoint, scope.availableEndpoints) { current, available ->
                    endpoints = available.associateBy { it.identifier.toString() }
                    CallAudio(current.toRoute(), available.map { it.toRoute() }.toImmutableList())
                }.collect { mutableAudio.value = it }
            }
        val muteJob = scope.launch { scope.isMuted.collect { mutableMuted.value = it } }
        scope.launch {
            for (command in commands) {
                when (command) {
                    Command.Answer -> {
                        scope.answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL)
                    }

                    Command.Active -> {
                        scope.setActive()
                    }

                    Command.Inactive -> {
                        scope.setInactive()
                    }

                    is Command.Route -> {
                        endpoints[command.route.id]?.let { scope.requestEndpointChange(it) }
                    }

                    is Command.End -> {
                        scope.disconnect(DisconnectCause(command.cause.toTelecom()))
                        // addCall returns once every coroutine of its scope is done.
                        audioJob.cancel()
                        muteJob.cancel()
                        commands.close()
                    }
                }
            }
        }
    }

    /** No Telecom for this call: the audio mode and route are set directly. */
    fun fallBack() {
        val call = AudioManagerCall(context)
        fallback = call
        mutableAudio.value = call.audio
        // Commands queued before Telecom refused: only the last route and the end matter.
        while (true) {
            val command = commands.tryReceive().getOrNull() ?: break
            if (command is Command.Route) select(call, command.route)
            if (command is Command.End) call.end()
        }
    }

    override fun answer() = send(Command.Answer)

    override fun setActive() {
        fallback?.start() ?: send(Command.Active)
    }

    override fun setInactive() = send(Command.Inactive)

    override fun selectRoute(route: AudioRoute) {
        val call = fallback
        if (call != null) select(call, route) else send(Command.Route(route))
    }

    private fun select(
        call: AudioManagerCall,
        route: AudioRoute,
    ) {
        call.select(route)
        mutableAudio.value = call.audio
    }

    override fun end(cause: SystemEndCause) {
        val call = fallback
        if (call != null) call.end() else send(Command.End(cause))
    }

    private fun send(command: Command) {
        if (fallback == null) commands.trySend(command)
    }
}

private fun SystemEndCause.toTelecom(): Int =
    when (this) {
        SystemEndCause.Local -> DisconnectCause.LOCAL
        SystemEndCause.Remote -> DisconnectCause.REMOTE
        SystemEndCause.Rejected -> DisconnectCause.REJECTED
        SystemEndCause.Missed -> DisconnectCause.MISSED
        SystemEndCause.Error -> DisconnectCause.ERROR
    }

private fun CallEndpointCompat.toRoute(): AudioRoute =
    AudioRoute(
        id = identifier.toString(),
        type =
            when (type) {
                CallEndpointCompat.TYPE_SPEAKER -> AudioRouteType.Speaker
                CallEndpointCompat.TYPE_BLUETOOTH -> AudioRouteType.Bluetooth
                CallEndpointCompat.TYPE_WIRED_HEADSET -> AudioRouteType.WiredHeadset
                else -> AudioRouteType.Earpiece
            },
        name = if (type == CallEndpointCompat.TYPE_BLUETOOTH) name.toString() else "",
    )
