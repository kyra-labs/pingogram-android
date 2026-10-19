package dev.kyralabs.pingogram.core.data.attachment

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

// Fakes of the Android seams of attachment preparation, so it runs on the JVM.

/** Picked content by URI: what the provider tells about it, its bytes, and phone contacts. */
internal class FakeContentSource : ContentSource {
    val files = mutableMapOf<String, Pair<ContentInfo, ByteArray>>()
    val contacts = mutableMapOf<String, PhoneContact>()

    /** What [open] returns instead of the bytes, e.g. a stream that fails or never ends. */
    var openOverride: ((String) -> InputStream?)? = null

    fun add(
        uri: String,
        name: String?,
        mimeType: String?,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
        size: Long? = bytes.size.toLong(),
    ) {
        files[uri] = ContentInfo(name, size, mimeType) to bytes
    }

    override fun describe(uri: String): ContentInfo = files[uri]?.first ?: ContentInfo(null, null, null)

    override fun open(uri: String): InputStream? =
        openOverride?.invoke(uri) ?: files[uri]?.second?.let(::ByteArrayInputStream)

    override fun phoneContact(uri: String): PhoneContact? = contacts[uri]
}

/**
 * Images and videos by file content: [images] and [videos] map the first byte of a file to what it decodes as; other
 * files are neither. Written photos and thumbnails hold [writtenBytes]; [plans] records each photo's plan.
 */
internal class FakeMediaProcessor : MediaProcessor {
    val images = mutableMapOf<Byte, ImageInfo>()
    val videos = mutableMapOf<Byte, VideoInfo>()
    var canWritePhotos = true
    var canWriteThumbnails = true
    val plans = mutableListOf<PhotoPlan>()
    val thumbnails = mutableListOf<Pair<Int, Int>>()

    override fun imageInfo(file: File): ImageInfo? = images[file.firstByte()]

    override fun writePhoto(
        source: File,
        plan: PhotoPlan,
        target: File,
    ): Boolean {
        plans += plan
        if (!canWritePhotos) return false
        target.writeBytes(writtenBytes)
        return true
    }

    override fun videoInfo(file: File): VideoInfo? = videos[file.firstByte()]

    override fun writeVideoThumbnail(
        source: File,
        width: Int,
        height: Int,
        target: File,
    ): Boolean {
        thumbnails += width to height
        if (!canWriteThumbnails) return false
        target.writeBytes(writtenBytes)
        return true
    }

    private fun File.firstByte(): Byte? = readBytes().firstOrNull()

    companion object {
        val writtenBytes = ByteArray(10) { 7 }
    }
}
