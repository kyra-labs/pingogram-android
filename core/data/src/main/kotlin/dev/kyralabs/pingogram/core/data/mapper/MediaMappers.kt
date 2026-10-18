package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import org.drinkless.tdlib.TdApi

/** The reference to a TDLib file; its size falls back to TDLib's estimate while the exact size is unknown. */
internal fun TdApi.File.toFileRef(): FileRef = FileRef(id = id, size = if (size > 0) size else expectedSize)

internal fun TdApi.Minithumbnail.toMinithumbnail(): Minithumbnail = Minithumbnail(width, height, data)
