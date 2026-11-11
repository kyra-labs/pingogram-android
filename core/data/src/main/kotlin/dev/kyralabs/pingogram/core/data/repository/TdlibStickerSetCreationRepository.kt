package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.attachment.AndroidContentSource
import dev.kyralabs.pingogram.core.data.attachment.AndroidMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.AttachmentPreparer
import dev.kyralabs.pingogram.core.data.attachment.ContentSource
import dev.kyralabs.pingogram.core.data.attachment.MediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetLimits
import dev.kyralabs.pingogram.core.domain.model.StickerSetNameCheck
import dev.kyralabs.pingogram.core.domain.repository.StickerSetCreationRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * [StickerSetCreationRepository] on TDLib, without a cache. A sticker is prepared as picked photos are
 * ([AttachmentPreparer.prepareSticker], on [io]): copied, decoded, written anew as a WebP whose longer side is 512 px.
 * The set is made with `createNewStickerSet` as the signed-in user (`getMe`), static WebP stickers each with one emoji,
 * which TDLib uploads before it answers, so the call waits as long as an upload may take.
 */
@AccountScoped
class TdlibStickerSetCreationRepository internal constructor(
    private val client: TdlibClient,
    private val files: UploadFiles,
    content: ContentSource,
    media: MediaProcessor,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
) : StickerSetCreationRepository {
    @Inject
    constructor(
        client: TdlibClient,
        files: UploadFiles,
        @ApplicationContext context: Context,
        @Dispatcher(IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ) : this(client, files, AndroidContentSource(context.contentResolver), AndroidMediaProcessor(), io, scope)

    private val preparer = AttachmentPreparer(files, content, media)

    override suspend fun suggestName(title: String): Outcome<String> =
        client.request(TdApi.GetSuggestedStickerSetName(title.trim())).map { it.text }

    override suspend fun checkName(name: String): Outcome<StickerSetNameCheck> {
        if (!StickerSetLimits.isNameWellFormed(name)) return Outcome.Success(StickerSetNameCheck.Invalid)
        return client.request(TdApi.CheckStickerSetName(name)).map { result ->
            when (result) {
                is TdApi.CheckStickerSetNameResultOk -> StickerSetNameCheck.Available
                is TdApi.CheckStickerSetNameResultNameOccupied -> StickerSetNameCheck.Occupied
                else -> StickerSetNameCheck.Invalid
            }
        }
    }

    override suspend fun prepareSticker(uri: String): Outcome<String> =
        withContext(io) { preparer.prepareSticker(uri, StickerSetLimits.STICKER_SIDE_PX) }

    override fun discardSticker(path: String) {
        scope.launch(io) { files.deleteUploads(listOf(path)) }
    }

    override suspend fun create(
        title: String,
        name: String,
        stickers: List<StickerDraft>,
    ): Outcome<Long> {
        val trimmed = title.trim()
        val refusal =
            when {
                trimmed.isEmpty() -> DomainError.TitleInvalid
                stickers.isEmpty() || stickers.size > StickerSetLimits.STICKER_COUNT_MAX -> DomainError.InputRejected
                else -> null
            }
        if (refusal != null) return Outcome.Failure(refusal)
        return when (val me = client.request(TdApi.GetMe())) {
            is Outcome.Failure -> me
            is Outcome.Success -> publish(me.value.id, trimmed, name, stickers)
        }
    }

    /** Sends `createNewStickerSet` as user [userId]: static WebP stickers, each with its one emoji. */
    private suspend fun publish(
        userId: Long,
        title: String,
        name: String,
        stickers: List<StickerDraft>,
    ): Outcome<Long> {
        val newStickers =
            stickers
                .map { sticker ->
                    TdApi.NewSticker(
                        TdApi.InputFileLocal(sticker.path),
                        TdApi.StickerFormatWebp(),
                        sticker.emoji,
                        null,
                        emptyArray(),
                    )
                }.toTypedArray()
        val function =
            TdApi.CreateNewStickerSet(userId, title, name, TdApi.StickerTypeRegular(), false, newStickers, SOURCE)
        return client.request(function, timeout = UPLOAD_TIMEOUT).map { it.id }
    }

    private companion object {
        /** Where the set was made, which Telegram keeps for its statistics: the app, as its own apps name theirs. */
        const val SOURCE = "Pingogram"

        /** TDLib answers once every sticker is uploaded: up to 120 files of 512 KB at most. */
        val UPLOAD_TIMEOUT = 10.minutes
    }
}
