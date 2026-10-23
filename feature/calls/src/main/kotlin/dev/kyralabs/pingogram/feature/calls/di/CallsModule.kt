package dev.kyralabs.pingogram.feature.calls.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.feature.calls.CallClock
import dev.kyralabs.pingogram.feature.calls.CallForeground
import dev.kyralabs.pingogram.feature.calls.CallTones
import dev.kyralabs.pingogram.feature.calls.CallerNames
import dev.kyralabs.pingogram.feature.calls.ChatTitles
import dev.kyralabs.pingogram.feature.calls.engine.CallEngine
import dev.kyralabs.pingogram.feature.calls.engine.NtgCallsEngine
import dev.kyralabs.pingogram.feature.calls.service.CallService
import dev.kyralabs.pingogram.feature.calls.service.RepositoryChatTitles
import dev.kyralabs.pingogram.feature.calls.service.SystemCallClock
import dev.kyralabs.pingogram.feature.calls.service.ToneGeneratorCallTones
import dev.kyralabs.pingogram.feature.calls.service.UserCallerNames
import dev.kyralabs.pingogram.feature.calls.system.CallSystem
import dev.kyralabs.pingogram.feature.calls.system.TelecomCallSystem
import dev.kyralabs.pingogram.feature.calls.ui.AndroidCameraAccess
import dev.kyralabs.pingogram.feature.calls.ui.AndroidMicrophoneAccess
import dev.kyralabs.pingogram.feature.calls.ui.CameraAccess
import dev.kyralabs.pingogram.feature.calls.ui.MicrophoneAccess

/** The call managers' parts on the device: the engine, Telecom, the service, the tones, the clock and the names. */
@Module
@InstallIn(SingletonComponent::class)
internal interface CallsModule {
    @Binds
    fun callEngine(engine: NtgCallsEngine): CallEngine

    @Binds
    fun callSystem(system: TelecomCallSystem): CallSystem

    @Binds
    fun callForeground(starter: CallService.Starter): CallForeground

    @Binds
    fun callTones(tones: ToneGeneratorCallTones): CallTones

    @Binds
    fun callerNames(names: UserCallerNames): CallerNames

    @Binds
    fun chatTitles(titles: RepositoryChatTitles): ChatTitles

    @Binds
    fun microphoneAccess(access: AndroidMicrophoneAccess): MicrophoneAccess

    @Binds
    fun cameraAccess(access: AndroidCameraAccess): CameraAccess

    companion object {
        @Provides
        fun callClock(): CallClock = SystemCallClock
    }
}
