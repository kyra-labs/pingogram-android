package dev.kyralabs.pingogram.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.ui.sticker.AnimatedStickers
import dev.kyralabs.pingogram.core.ui.sticker.LottieStickerDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * The player of animated stickers (`core:ui` [AnimatedStickers], `docs/adr/0008-lottie-for-animated-stickers.md`),
 * which `MainActivity` hands to the composition. It forgets its animations whenever TDLib closes, as the image loader
 * does: file ids belong to one TDLib instance.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnimatedStickersModule {
    @Provides
    @Singleton
    fun animatedStickers(
        fileRepository: FileRepository,
        authRepository: AuthRepository,
        @ApplicationScope scope: CoroutineScope,
    ): AnimatedStickers =
        AnimatedStickers(fileRepository, LottieStickerDecoder()).also { stickers ->
            scope.launch { authRepository.authStep.filterIsInstance<AuthStep.Closed>().collect { stickers.clear() } }
        }
}
