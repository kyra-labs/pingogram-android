package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toAnimation
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.repository.SavedGifRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.flow.Flow
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SavedGifRepository] on TDLib: `getSavedAnimations`, read again on `updateSavedAnimations` (which carries only file
 * ids), `removeSavedAnimation` by the file's id, and the option `animation_search_bot_username`. TDLib keeps the
 * saved GIFs in its database, so no cache of its own.
 */
@Singleton
class TdlibSavedGifRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : SavedGifRepository {
        override fun savedGifs(): Flow<Outcome<List<Animation>>> =
            client.readOn({ it is TdApi.UpdateSavedAnimations }) {
                client.request(TdApi.GetSavedAnimations()).map { result ->
                    result.animations.orEmpty().map { it.toAnimation() }
                }
            }

        override suspend fun removeSaved(gif: Animation): Outcome<Unit> =
            client.request(TdApi.RemoveSavedAnimation(TdApi.InputFileId(gif.file.id))).map { }

        override suspend fun searchBotUsername(): Outcome<String?> =
            client.request(TdApi.GetOption(SEARCH_BOT_OPTION)).map { value ->
                (value as? TdApi.OptionValueString)?.value?.takeIf { it.isNotBlank() }
            }

        private companion object {
            const val SEARCH_BOT_OPTION = "animation_search_bot_username"
        }
    }
