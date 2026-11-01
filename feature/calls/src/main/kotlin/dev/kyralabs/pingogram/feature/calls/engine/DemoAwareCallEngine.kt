package dev.kyralabs.pingogram.feature.calls.engine

import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.repository.DemoRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [CallEngine] the call managers run: NTgCalls, or the demo's engine while the demo shows
 * (`docs/adr/0034-demo-mode-for-store-review.md`), which connects to nothing and never loads NTgCalls' library. The
 * demo turns on and off only with the app opened again, never during a call.
 */
@Singleton
internal class DemoAwareCallEngine
    @Inject
    constructor(
        private val real: NtgCallsEngine,
        private val demoEngine: DemoCallEngine,
        private val demo: DemoRepository,
    ) : CallEngine {
        private val engine: CallEngine get() = if (demo.isDemo.value) demoEngine else real

        override suspend fun protocol(): CallProtocol = engine.protocol()

        override suspend fun cameras(): List<CallCamera> = engine.cameras()

        override suspend fun connect(
            callId: Int,
            isOutgoing: Boolean,
            connection: CallConnection,
        ): CallSession = engine.connect(callId, isOutgoing, connection)

        override suspend fun createGroupCall(
            groupCallId: Int,
            muted: Boolean,
        ): GroupCallSession = engine.createGroupCall(groupCallId, muted)

        override suspend fun createConference(
            muted: Boolean,
            encryption: ConferenceEncryption,
        ): GroupCallSession = engine.createConference(muted, encryption)
    }

/** The screen capture's consent goes to NTgCalls, except in the demo, which captures nothing. */
@Singleton
internal class DemoAwareScreenCaptureConsent
    @Inject
    constructor(
        private val real: NtgScreenCaptureConsent,
        private val demo: DemoRepository,
    ) : ScreenCaptureConsent {
        override fun grant(result: Any) {
            if (!demo.isDemo.value) real.grant(result)
        }
    }
