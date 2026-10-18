package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import org.drinkless.tdlib.TdApi

/** The reference to a TDLib file; its size falls back to TDLib's estimate while the exact size is unknown. */
internal fun TdApi.File.toFileRef(): FileRef = FileRef(id = id, size = if (size > 0) size else expectedSize)

internal fun TdApi.Minithumbnail.toMinithumbnail(): Minithumbnail = Minithumbnail(width, height, data)

internal fun TdApi.ProfilePhoto.toProfilePhoto(): ProfilePhoto = profilePhoto(small, big, minithumbnail)

/** A chat's photo: the same files as a user's [TdApi.ProfilePhoto], which it is for private chats. */
internal fun TdApi.ChatPhotoInfo.toProfilePhoto(): ProfilePhoto = profilePhoto(small, big, minithumbnail)

private fun profilePhoto(
    small: TdApi.File,
    big: TdApi.File,
    minithumbnail: TdApi.Minithumbnail?,
) = ProfilePhoto(small = small.toFileRef(), big = big.toFileRef(), minithumbnail = minithumbnail?.toMinithumbnail())
