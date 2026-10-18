package dev.kyralabs.pingogram.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibConnectionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCountryRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibFileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibMessageRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUpdateCache
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.domain.repository.MessageRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository

/**
 * Binds the repositories. Each [TdlibUpdateCache] also goes into the set that `PingogramApplication` injects, so it
 * exists, and is subscribed to TDLib's updates, before TDLib starts.
 */
@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun authRepository(repository: TdlibAuthRepository): AuthRepository

    @Binds
    fun connectionRepository(repository: TdlibConnectionRepository): ConnectionRepository

    @Binds
    fun countryRepository(repository: TdlibCountryRepository): CountryRepository

    @Binds
    fun userRepository(repository: TdlibUserRepository): UserRepository

    @Binds
    @IntoSet
    fun userCache(repository: TdlibUserRepository): TdlibUpdateCache

    @Binds
    fun fileRepository(repository: TdlibFileRepository): FileRepository

    @Binds
    @IntoSet
    fun fileCache(repository: TdlibFileRepository): TdlibUpdateCache

    @Binds
    fun chatRepository(repository: TdlibChatRepository): ChatRepository

    @Binds
    @IntoSet
    fun chatCache(repository: TdlibChatRepository): TdlibUpdateCache

    @Binds
    fun messageRepository(repository: TdlibMessageRepository): MessageRepository

    @Binds
    @IntoSet
    fun messageCache(repository: TdlibMessageRepository): TdlibUpdateCache
}
