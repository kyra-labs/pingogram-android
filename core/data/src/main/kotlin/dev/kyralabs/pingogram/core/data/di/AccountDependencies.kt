package dev.kyralabs.pingogram.core.data.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.Default
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.export.DocumentTree
import dev.kyralabs.pingogram.core.data.repository.AndroidNetworkRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactSyncSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceContactsRepository
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.domain.repository.PushTokenSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.util.Optional

/**
 * What an account's graph ([AccountComponent]) takes from the app's: the device's parts, the app's own settings, the
 * dispatchers and the app's scope. `:app` hands it over from Hilt's graph, which holds one of each for every account.
 * A new account repository that needs something else from the app adds it here.
 */
@Suppress("TooManyFunctions")
interface AccountDependencies {
    @ApplicationContext
    fun context(): Context

    @ApplicationScope
    fun applicationScope(): CoroutineScope

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

    fun linkPreviewSettingsRepository(): LinkPreviewSettingsRepository

    fun contactSyncSettingsRepository(): ContactSyncSettingsRepository

    fun pushTokenSource(): Optional<PushTokenSource>
}
