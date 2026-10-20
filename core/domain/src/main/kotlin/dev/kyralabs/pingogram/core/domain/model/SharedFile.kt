package dev.kyralabs.pingogram.core.domain.model

/**
 * A downloaded file to copy into shared storage (`SharedStorageRepository`): the file at [path], of the type its
 * extension names or else [fallbackMimeType], saved as [baseName] with the file's extension. The name says nothing
 * about the chat or the sender: a document keeps the name it was sent with, anything else is named after its message.
 */
data class SharedFile(
    val path: String,
    val fallbackMimeType: String,
    val baseName: String,
) {
    // The path and a document's name can be personal.
    override fun toString() = "SharedFile(fallbackMimeType=$fallbackMimeType, …)"
}
