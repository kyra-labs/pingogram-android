package dev.kyralabs.pingogram.core.data.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.export.DocumentTree
import dev.kyralabs.pingogram.core.data.repository.AndroidNetworkRepository
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceContactsRepository
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.LockedChatIdsRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.domain.repository.PushTokenSource
import kotlinx.coroutines.CoroutineDispatcher
import java.util.Optional

/**
 * What an account's graph ([AccountComponent]) takes from the app's: the device's parts, the app's own settings and
 * the dispatchers; its scope is its own ([AccountComponent.Factory]). `:app` hands it over from Hilt's graph, which
 * holds one of each for every account. A new account repository that needs something else from the app adds it here.
 */
@Suppress("TooManyFunctions")
interface AccountDependencies {
    @ApplicationContext
    fun context(): Context

    @Dispatcher(IO)
    fun ioDispatcher(): CoroutineDispatcher

    @Dispatcher(Default)
    fun defaultDispatcher(): CoroutineDispatcher

    fun wallClock(): WallClock

    fun uploadFiles(): UploadFiles

    fun androidNetworkRepository(): AndroidNetworkRepository

    fun documentTree(): DocumentTree

    fun deviceContactsRepository(): DeviceContactsRepository

    fun passcodeRepository(): PasscodeRepository

    /** The passcode lock is the app's (`DeviceModule`); the chat lock of each account follows it. */
    fun appLockRepository(): AppLockRepository

    fun linkPreviewSettingsRepository(): LinkPreviewSettingsRepository

    fun contactSyncSettingsRepository(): ContactSyncSettingsRepository

    fun lockedChatIdsRepository(): LockedChatIdsRepository

    fun pushTokenSource(): Optional<PushTokenSource>
}
