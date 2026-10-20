package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoDownloadTest {
    private val everything =
        AutoDownloadRules(
            photos = true,
            videos = true,
            videoSizeLimit = 10_000_000,
            files = true,
            fileSizeLimit = 3_000_000,
            voiceAndVideoMessages = true,
        )

    @Test
    fun `the defaults are PERFORMANCE section 7's`() {
        val settings = AutoDownloadSettings()

        assertEquals(
            AutoDownloadRules(photos = true, videos = true, files = true, voiceAndVideoMessages = true),
            settings.wifi,
        )
        assertEquals(10_000_000L, settings.wifi.videoSizeLimit)
        assertEquals(3_000_000L, settings.wifi.fileSizeLimit)
        assertEquals(AutoDownloadRules(photos = true), settings.mobile)
        assertTrue(settings.roaming.downloadsNothing)
    }

    @Test
    fun `videos and GIFs download within their limit`() {
        assertEquals(FileRef(1, 10_000_000), everything.fileToDownload(video(FileRef(1, 10_000_000))))
        assertEquals(FileRef(2, 2_000_000), everything.fileToDownload(animation(FileRef(2, 2_000_000))))
        assertNull(everything.fileToDownload(video(FileRef(1, 10_000_001))))
        assertNull(everything.copy(videos = false).fileToDownload(video(FileRef(1, 1_000))))
    }

    @Test
    fun `documents and music download within the file limit`() {
        assertEquals(FileRef(3, 3_000_000), everything.fileToDownload(document(FileRef(3, 3_000_000))))
        assertEquals(FileRef(4, 1_000), everything.fileToDownload(audio(FileRef(4, 1_000))))
        assertNull(everything.fileToDownload(document(FileRef(3, 3_000_001))))
        assertNull(everything.copy(files = false).fileToDownload(audio(FileRef(4, 1_000))))
    }

    @Test
    fun `voice and video messages download whatever their size, if on`() {
        assertEquals(FileRef(5, 50_000_000), everything.fileToDownload(voiceNote(FileRef(5, 50_000_000))))
        assertEquals(FileRef(6, 1_000), everything.fileToDownload(videoNote(FileRef(6, 1_000))))
        assertNull(everything.copy(voiceAndVideoMessages = false).fileToDownload(voiceNote(FileRef(5, 1_000))))
    }

    @Test
    fun `a size TDLib does not know yet counts as within the limit`() {
        assertEquals(FileRef(1, 0), everything.fileToDownload(video(FileRef(1, 0))))
    }

    @Test
    fun `self-destructing media never downloads by itself`() {
        assertNull(everything.fileToDownload(video(FileRef(1, 1_000), isSecret = true)))
        assertNull(everything.fileToDownload(videoNote(FileRef(6, 1_000), isSecret = true)))
    }

    @Test
    fun `photos and other content have no file to download here`() {
        val photo =
            MessageContent.Photo(
                Photo(sizes = persistentListOf(), minithumbnail = null),
                FormattedText.Empty,
                hasSpoiler = false,
                isSecret = false,
                showCaptionAboveMedia = false,
            )

        assertNull(everything.fileToDownload(photo))
        assertNull(everything.fileToDownload(MessageContent.Text(FormattedText("Hi", persistentListOf()), null)))
    }

    @Test
    fun `the rules in effect are the network's, and nothing offline or while Data Saver holds a metered network`() {
        val settings = AutoDownloadSettings()

        assertEquals(settings.wifi, settings.rulesOn(NetworkStatus(NetworkType.WiFi, false, isDataSaverOn = true)))
        assertEquals(settings.mobile, settings.rulesOn(NetworkStatus(NetworkType.Mobile, true, false)))
        assertEquals(settings.roaming, settings.rulesOn(NetworkStatus(NetworkType.Roaming, true, false)))
        assertEquals(AutoDownloadRules.Nothing, settings.rulesOn(NetworkStatus(NetworkType.Mobile, true, true)))
        assertEquals(AutoDownloadRules.Nothing, settings.rulesOn(NetworkStatus.Offline))
    }

    @Test
    fun `with replaces one network's rules`() {
        val changed = AutoDownloadSettings().with(AutoDownloadNetwork.Roaming, everything)

        assertEquals(everything, changed.rulesFor(AutoDownloadNetwork.Roaming))
        assertEquals(AutoDownloadSettings().wifi, changed.rulesFor(AutoDownloadNetwork.WiFi))
    }

    @Test
    fun `the size limits rise and hold the defaults`() {
        assertEquals(AutoDownloadSizeLimits.sorted(), AutoDownloadSizeLimits)
        assertTrue(AutoDownloadRules.DEFAULT_VIDEO_SIZE_LIMIT in AutoDownloadSizeLimits)
        assertTrue(AutoDownloadRules.DEFAULT_FILE_SIZE_LIMIT in AutoDownloadSizeLimits)
    }

    private fun video(
        file: FileRef,
        isSecret: Boolean = false,
    ) = MessageContent.Video(
        Video(1, 640, 480, null, null, supportsStreaming = true, file = file),
        FormattedText.Empty,
        hasSpoiler = false,
        isSecret = isSecret,
        showCaptionAboveMedia = false,
    )

    private fun animation(file: FileRef) =
        MessageContent.Animation(
            Animation(1, 320, 240, "video/mp4", null, null, file),
            FormattedText.Empty,
            hasSpoiler = false,
            isSecret = false,
            showCaptionAboveMedia = false,
        )

    private fun document(file: FileRef) =
        MessageContent.Document(Document("report.pdf", "application/pdf", null, null, file), FormattedText.Empty)

    private fun audio(file: FileRef) =
        MessageContent.Audio(Audio(60, "Song", "Band", "song.mp3", null, null, file), FormattedText.Empty)

    private fun voiceNote(file: FileRef) =
        MessageContent.VoiceNote(VoiceNote(5, Waveform(ByteArray(0)), file), FormattedText.Empty, isListened = false)

    private fun videoNote(
        file: FileRef,
        isSecret: Boolean = false,
    ) = MessageContent.VideoNote(VideoNote(5, 240, null, null, file), isViewed = false, isSecret = isSecret)
}
