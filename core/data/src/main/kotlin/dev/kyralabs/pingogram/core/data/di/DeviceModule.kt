package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.data.emoji.AndroidEmojiCatalogRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidBatterySaverRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidDeviceContactsRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidDeviceLanguageDetector
import dev.kyralabs.pingogram.core.data.repository.AndroidDeviceLocationRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidNetworkRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidPickedPhotoRepository
import dev.kyralabs.pingogram.core.data.repository.AndroidSharedStorageRepository
import dev.kyralabs.pingogram.core.data.repository.DefaultAccountGraphs
import dev.kyralabs.pingogram.core.data.repository.DefaultAppLockRepository
import dev.kyralabs.pingogram.core.data.repository.DefaultAutoDownloadPolicy
import dev.kyralabs.pingogram.core.data.repository.DefaultDeviceAccountsRepository
import dev.kyralabs.pingogram.core.data.repository.DefaultPowerSavingPolicy
import dev.kyralabs.pingogram.core.domain.repository.AccountGraphs
import dev.kyralabs.pingogram.core.domain.repository.AppLockRepository
import dev.kyralabs.pingogram.core.domain.repository.AutoDownloadPolicy
import dev.kyralabs.pingogram.core.domain.repository.BatterySaverRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceAccountsRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceContactsRepository
import dev.kyralabs.pingogram.core.domain.repository.DeviceLanguageDetector
import dev.kyralabs.pingogram.core.domain.repository.DeviceLocationRepository
import dev.kyralabs.pingogram.core.domain.repository.EmojiCatalogRepository
import dev.kyralabs.pingogram.core.domain.repository.NetworkRepository
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository
import dev.kyralabs.pingogram.core.domain.repository.PowerSavingPolicy
import dev.kyralabs.pingogram.core.domain.repository.SharedStorageRepository

/**
 * What belongs to the device rather than to an account (`docs/adr/0031-multiple-accounts.md`): the network, Battery
 * Saver, the phone's contacts and location, shared storage, the emoji the fonts draw, the passcode lock. One instance
 * for every account.
 */
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
interface DeviceModule {
    /** The passcode lock is the app's: one for every account. */
    @Binds
    fun appLockRepository(repository: DefaultAppLockRepository): AppLockRepository

    @Binds
    fun deviceAccountsRepository(repository: DefaultDeviceAccountsRepository): DeviceAccountsRepository

    @Binds
    fun accountGraphs(graphs: DefaultAccountGraphs): AccountGraphs

    @Binds
    fun deviceContactsRepository(repository: AndroidDeviceContactsRepository): DeviceContactsRepository

    @Binds
    fun pickedPhotoRepository(repository: AndroidPickedPhotoRepository): PickedPhotoRepository

    @Binds
    fun sharedStorageRepository(repository: AndroidSharedStorageRepository): SharedStorageRepository

    @Binds
    fun deviceLocationRepository(repository: AndroidDeviceLocationRepository): DeviceLocationRepository

    @Binds
    fun emojiCatalogRepository(repository: AndroidEmojiCatalogRepository): EmojiCatalogRepository

    @Binds
    fun networkRepository(repository: AndroidNetworkRepository): NetworkRepository

    @Binds
    fun autoDownloadPolicy(policy: DefaultAutoDownloadPolicy): AutoDownloadPolicy

    @Binds
    fun batterySaverRepository(repository: AndroidBatterySaverRepository): BatterySaverRepository

    @Binds
    fun powerSavingPolicy(policy: DefaultPowerSavingPolicy): PowerSavingPolicy

    @Binds
    fun deviceLanguageDetector(detector: AndroidDeviceLanguageDetector): DeviceLanguageDetector
}
