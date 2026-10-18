package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileStateTest {
    @Test
    fun `progress is the downloaded share of the size`() {
        assertEquals(0.25f, FileState.Downloading(downloadedSize = 256, size = 1_024, isActive = true).progress)
    }

    @Test
    fun `progress is 0 while the size is unknown`() {
        assertEquals(0f, FileState.Downloading(downloadedSize = 256, size = 0, isActive = true).progress)
    }

    @Test
    fun `progress never exceeds 1, even when TDLib's estimate was too small`() {
        assertEquals(1f, FileState.Downloading(downloadedSize = 2_048, size = 1_024, isActive = true).progress)
    }

    @Test
    fun `toString of a downloaded file leaves its path out`() {
        assertEquals("Downloaded(size=1024, …)", FileState.Downloaded("/files/documents/report.pdf", 1_024).toString())
    }
}
