package dev.kyralabs.pingogram.core.domain.model

/**
 * The networks automatic download keeps rules of its own for (`REQUIREMENTS.md` FR-S1, Data and storage;
 * `docs/PERFORMANCE.md` §7), in the order Settings lists them.
 */
enum class AutoDownloadNetwork {
    Mobile,
    WiFi,
    Roaming,
}

/**
 * What a conversation downloads by itself on one kind of network (`docs/PERFORMANCE.md` §7); everything else waits for
 * a tap. Stickers, profile photos and the thumbnails of videos and files always download: they are a few kilobytes,
 * and the conversation would be blank without them.
 *
 * @property photos the photos in the bubbles, at the size they show at.
 * @property videos videos and GIFs of at most [videoSizeLimit] bytes, so they play at once in the media viewer.
 * @property files documents and music of at most [fileSizeLimit] bytes.
 * @property voiceAndVideoMessages voice messages and round video messages, so they play at once.
 */
data class AutoDownloadRules(
    val photos: Boolean = false,
    val videos: Boolean = false,
    val videoSizeLimit: Long = DEFAULT_VIDEO_SIZE_LIMIT,
    val files: Boolean = false,
    val fileSizeLimit: Long = DEFAULT_FILE_SIZE_LIMIT,
    val voiceAndVideoMessages: Boolean = false,
) {
    /** True when nothing downloads by itself. */
    val downloadsNothing: Boolean get() = !photos && !videos && !files && !voiceAndVideoMessages

    /**
     * The file of [content] to download as soon as its message shows, under these rules: a video's or GIF's, a
     * document's or song's, a voice or video message's; null for anything else, and for self-destructing media, which
     * only the viewer shows. Photos are not among them: the bubble downloads the size it shows itself, if [photos].
     * A size TDLib does not know yet (0) counts as within the limit.
     */
    fun fileToDownload(content: MessageContent): FileRef? =
        when (content) {
            is MessageContent.Video -> content.video.file.takeIf { !content.isSecret && isVideoAllowed(it) }
            is MessageContent.Animation -> content.animation.file.takeIf { !content.isSecret && isVideoAllowed(it) }
            is MessageContent.Document -> content.document.file.takeIf(::isFileAllowed)
            is MessageContent.Audio -> content.audio.file.takeIf(::isFileAllowed)
            is MessageContent.VoiceNote -> content.voiceNote.file.takeIf { voiceAndVideoMessages }
            is MessageContent.VideoNote -> content.videoNote.file.takeIf { voiceAndVideoMessages && !content.isSecret }
            else -> null
        }

    private fun isVideoAllowed(file: FileRef) = videos && file.size <= videoSizeLimit

    private fun isFileAllowed(file: FileRef) = files && file.size <= fileSizeLimit

    companion object {
        /** Nothing downloads by itself: roaming's default, and the rules while Data Saver holds back mobile data. */
        val Nothing = AutoDownloadRules()

        const val DEFAULT_VIDEO_SIZE_LIMIT = 10_000_000L
        const val DEFAULT_FILE_SIZE_LIMIT = 3_000_000L
    }
}

/**
 * The size limits the settings offer for videos and files, smallest first, in bytes (decimal megabytes, as Android
 * and the bubbles show sizes). The defaults are among them.
 */
val AutoDownloadSizeLimits: List<Long> =
    listOf(
        1_000_000L,
        3_000_000L,
        5_000_000L,
        10_000_000L,
        15_000_000L,
        25_000_000L,
        50_000_000L,
        100_000_000L,
        250_000_000L,
        500_000_000L,
        1_000_000_000L,
        2_000_000_000L,
        4_000_000_000L,
    )

/**
 * The automatic download rules of each kind of network, kept on this device across logouts like the Appearance
 * settings. The defaults are `docs/PERFORMANCE.md` §7's: on Wi-Fi photos, videos up to 10 MB, files up to 3 MB and voice
 * and video messages; on mobile data photos only; while roaming nothing.
 */
data class AutoDownloadSettings(
    val mobile: AutoDownloadRules = DefaultMobileRules,
    val wifi: AutoDownloadRules = DefaultWiFiRules,
    val roaming: AutoDownloadRules = AutoDownloadRules.Nothing,
) {
    fun rulesFor(network: AutoDownloadNetwork): AutoDownloadRules =
        when (network) {
            AutoDownloadNetwork.Mobile -> mobile
            AutoDownloadNetwork.WiFi -> wifi
            AutoDownloadNetwork.Roaming -> roaming
        }

    /** These settings with [rules] for [network]. */
    fun with(
        network: AutoDownloadNetwork,
        rules: AutoDownloadRules,
    ): AutoDownloadSettings =
        when (network) {
            AutoDownloadNetwork.Mobile -> copy(mobile = rules)
            AutoDownloadNetwork.WiFi -> copy(wifi = rules)
            AutoDownloadNetwork.Roaming -> copy(roaming = rules)
        }

    /**
     * The rules in effect on [status]: its network's, or [AutoDownloadRules.Nothing] without a network and while Data
     * Saver holds back a metered one ([NetworkStatus.savesData]), where Android asks apps to use as little data as
     * they can.
     */
    fun rulesOn(status: NetworkStatus): AutoDownloadRules {
        val network = status.type.autoDownloadNetwork
        return if (network == null || status.savesData) AutoDownloadRules.Nothing else rulesFor(network)
    }

    companion object {
        val DefaultMobileRules = AutoDownloadRules(photos = true)
        val DefaultWiFiRules =
            AutoDownloadRules(photos = true, videos = true, files = true, voiceAndVideoMessages = true)
    }
}
