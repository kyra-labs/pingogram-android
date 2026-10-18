package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileMappersTest {
    @Test
    fun `a remote file is not downloaded`() {
        assertEquals(
            FileState.NotDownloaded(size = 2_048, canBeDownloaded = true),
            TdApiFixtures.file(size = 2_048).toFileState(),
        )
    }

    @Test
    fun `a file that cannot be downloaded says so`() {
        assertEquals(
            FileState.NotDownloaded(size = 2_048, canBeDownloaded = false),
            TdApiFixtures.file(size = 2_048, canBeDownloaded = false).toFileState(),
        )
    }

    @Test
    fun `an active download reports its progress`() {
        assertEquals(
            FileState.Downloading(downloadedSize = 512, size = 2_048, isActive = true),
            TdApiFixtures.file(size = 2_048, downloadedSize = 512, isDownloadingActive = true).toFileState(),
        )
    }

    @Test
    fun `a download that just started is downloading`() {
        assertEquals(
            FileState.Downloading(downloadedSize = 0, size = 2_048, isActive = true),
            TdApiFixtures.file(size = 2_048, isDownloadingActive = true).toFileState(),
        )
    }

    @Test
    fun `a stopped partial download is downloading, but not active`() {
        assertEquals(
            FileState.Downloading(downloadedSize = 512, size = 2_048, isActive = false),
            TdApiFixtures.file(size = 2_048, downloadedSize = 512).toFileState(),
        )
    }

    @Test
    fun `a complete file has its path and size`() {
        val file =
            TdApiFixtures.file(
                size = 2_048,
                localPath = "/files/photos/7.jpg",
                downloadedSize = 2_048,
                isDownloadingCompleted = true,
            )

        assertEquals(FileState.Downloaded(path = "/files/photos/7.jpg", size = 2_048), file.toFileState())
    }

    @Test
    fun `the size falls back to TDLib's estimate while unknown`() {
        assertEquals(
            FileState.Downloading(downloadedSize = 100, size = 900, isActive = true),
            TdApiFixtures
                .file(size = 0, expectedSize = 900, downloadedSize = 100, isDownloadingActive = true)
                .toFileState(),
        )
    }

    @Test
    fun `download priorities follow the performance budget`() {
        assertEquals(
            listOf(32, 16, 1),
            listOf(DownloadPriority.Visible, DownloadPriority.Prefetch, DownloadPriority.Background)
                .map { it.tdlibPriority },
        )
    }
}
