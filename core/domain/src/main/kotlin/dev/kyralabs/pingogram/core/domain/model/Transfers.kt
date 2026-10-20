package dev.kyralabs.pingogram.core.domain.model

/**
 * The transfers the user started that have not ended (`TransferRepository`, `REQUIREMENTS.md` FR-N4): the files of the
 * messages being sent from this device ([uploads]) and the downloads the user asked for ([downloads]). Sizes are in
 * bytes, over the transfers whose size TDLib knows.
 */
data class Transfers(
    val uploads: Int = 0,
    val downloads: Int = 0,
    val transferredSize: Long = 0,
    val totalSize: Long = 0,
) {
    /** How many files are on their way. */
    val count: Int
        get() = uploads + downloads

    /** Whether nothing the user started is on its way. */
    val isEmpty: Boolean
        get() = count == 0

    /** From 0 to 1; null while TDLib knows none of the sizes. */
    val progress: Float?
        get() = if (totalSize > 0) (transferredSize.toFloat() / totalSize).coerceIn(0f, 1f) else null

    companion object {
        /** Nothing on its way. */
        val None = Transfers()
    }
}
