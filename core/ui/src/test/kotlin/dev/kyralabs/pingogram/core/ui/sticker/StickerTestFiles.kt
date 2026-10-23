package dev.kyralabs.pingogram.core.ui.sticker

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * A TGS file in [directory]: `sticker/pulse.json` (a yellow dot in a blue ring that grows over a second at 60 frames
 * per second, Pingogram's own test artwork) gzipped, as Telegram sends animated stickers.
 */
internal fun pulseTgs(directory: File): File {
    val json = checkNotNull(StickerTestFiles::class.java.getResourceAsStream("/sticker/pulse.json")).readBytes()
    return File(directory, "pulse.tgs").apply { GZIPOutputStream(outputStream()).use { it.write(json) } }
}

private object StickerTestFiles

/** Files whose state the test sets ([state]), one for every id; records downloads. */
internal class FakeStickerFiles : FileRepository {
    val state = MutableStateFlow<FileState?>(null)
    val downloads = mutableListOf<Pair<Int, DownloadPriority>>()
    var downloadResult: Outcome<Unit> = Outcome.Success(Unit)

    override fun observe(fileId: Int): Flow<FileState> = state.filterNotNull()

    override fun observeUpload(fileId: Int): Flow<FileUpload> = emptyFlow()

    override suspend fun download(
        fileId: Int,
        priority: DownloadPriority,
    ): Outcome<Unit> {
        downloads += fileId to priority
        return downloadResult
    }

    override suspend fun cancelDownload(fileId: Int): Outcome<Unit> = Outcome.Success(Unit)
}
