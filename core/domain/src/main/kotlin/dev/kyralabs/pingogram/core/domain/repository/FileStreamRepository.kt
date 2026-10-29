package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome

/**
 * Reading a TDLib file while it downloads (`docs/adr/0028-streaming-media.md`): videos and songs play before the whole
 * file is on the device. Reading from an offset downloads from there at visible priority, so a seek downloads from
 * the new place rather than everything before it.
 */
interface FileStreamRepository {
    /**
     * Waits until at least [length] bytes of file [fileId] from [offset] are on the device (fewer at the file's end),
     * downloading from [offset] if no download covers it, and answers where they are. Fails with
     * `DomainError.NotFound` for a file TDLib does not know, `DomainError.Network` when the download stopped.
     */
    suspend fun awaitRange(
        fileId: Int,
        offset: Long,
        length: Long,
    ): Outcome<FileRange>

    /** Stops the download that reading [fileId] started, unless the file is complete; what came stays on the device. */
    suspend fun stop(fileId: Int)
}

/** Where bytes of a file are: [available] of them from the asked offset in the file at [path], [size] in all. */
data class FileRange(
    val path: String,
    val available: Long,
    val size: Long,
) {
    // The path ends in the file's name, which can be personal.
    override fun toString() = "FileRange(available=$available, size=$size, …)"
}
