package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.attachment.AndroidMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.MediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.Orientation
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.attachment.stickerPlan
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.StickerDraft
import dev.kyralabs.pingogram.core.domain.model.StickerSetLimits
import dev.kyralabs.pingogram.core.domain.model.StickerSetSummary
import dev.kyralabs.pingogram.core.domain.repository.StickerMakerRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * [StickerMakerRepository] on TDLib, without a cache: td_api.tl announces no change to the user's own sets. The
 * editor's drawing is written as stickers picked from the gallery are ([MediaProcessor.writeSticker], [stickerPlan],
 * on [io]); `addStickerToSet` goes as the signed-in user (`getMe`) with a static WebP sticker and its one emoji, which
 * TDLib uploads before it answers.
 *
 * Nothing about the image or its path is logged.
 */
@AccountScoped
class TdlibStickerMakerRepository internal constructor(
    private val client: TdlibClient,
    private val files: UploadFiles,
    private val media: MediaProcessor,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
) : StickerMakerRepository {
    @Inject
    constructor(
        client: TdlibClient,
        files: UploadFiles,
        @Dispatcher(IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ) : this(client, files, AndroidMediaProcessor(), io, scope)

    override suspend fun ownedSets(): Outcome<List<StickerSetSummary>> =
        client.request(TdApi.GetOwnedStickerSets(0, OWNED_LIMIT)).map { result ->
            result.sets
                .orEmpty()
                .filter { it.stickerType is TdApi.StickerTypeRegular && it.size < StickerSetLimits.STICKER_COUNT_MAX }
                .map { it.toStickerSetSummary() }
        }

    override suspend fun prepareSticker(image: ByteArray): Outcome<String> =
        withContext(io) {
            if (image.size > MAX_IMAGE_BYTES) return@withContext Outcome.Failure(DomainError.FileTooLarge)
            val directory =
                try {
                    files.newUploadDirectory()
                } catch (_: IOException) {
                    return@withContext Outcome.Failure(DomainError.FileUnavailable)
                }
            val written = write(image, directory)
            if (written == null) directory.deleteRecursively()
            written?.let { Outcome.Success(it.path) } ?: Outcome.Failure(DomainError.PhotoInvalid)
        }

    /** [image] as a sticker in [directory]; null when it cannot be decoded or written. */
    private fun write(
        image: ByteArray,
        directory: File,
    ): File? {
        val source = File(directory, SOURCE_NAME)
        val target = File(directory, STICKER_NAME)
        return try {
            source.writeBytes(image)
            val info = media.imageInfo(source) ?: return null
            // The editor draws upright: whatever the photo's own orientation was, its drawing has none.
            val plan = stickerPlan(info.width, info.height, Orientation.Normal, StickerSetLimits.STICKER_SIDE_PX)
            target.takeIf { media.writeSticker(source, plan, it) && it.length() > 0 }
        } catch (_: IOException) {
            null
        } finally {
            source.delete()
        }
    }

    override fun discardSticker(path: String) {
        scope.launch(io) { files.deleteUploads(listOf(path)) }
    }

    override suspend fun addSticker(
        setName: String,
        sticker: StickerDraft,
    ): Outcome<Unit> =
        when (val me = client.request(TdApi.GetMe())) {
            is Outcome.Failure -> {
                me
            }

            is Outcome.Success -> {
                val newSticker =
                    TdApi.NewSticker(
                        TdApi.InputFileLocal(sticker.path),
                        TdApi.StickerFormatWebp(),
                        sticker.emoji,
                        null,
                        emptyArray(),
                    )
                client
                    .request(TdApi.AddStickerToSet(me.value.id, setName, newSticker), timeout = UPLOAD_TIMEOUT)
                    .map { }
            }
        }

    private companion object {
        /** The first page of the user's sets: Telegram lists more only to those who made over a hundred. */
        const val OWNED_LIMIT = 100

        /** The editor's drawing is a PNG of at most a few megabytes; anything larger is no drawing of it. */
        const val MAX_IMAGE_BYTES = 32 * 1024 * 1024

        const val SOURCE_NAME = "drawing.png"
        const val STICKER_NAME = "sticker.webp"

        /** TDLib answers once the sticker is uploaded: one file of 512 KB at most. */
        val UPLOAD_TIMEOUT = 2.minutes
    }
}
