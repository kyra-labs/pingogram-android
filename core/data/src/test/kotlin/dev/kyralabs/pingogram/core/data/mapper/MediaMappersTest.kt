package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MediaMappersTest {
    @Test
    fun `a file maps to its id and size`() {
        assertEquals(FileRef(id = 7, size = 2_048), TdApiFixtures.file(id = 7, size = 2_048).toFileRef())
    }

    @Test
    fun `a file of unknown size uses the expected size`() {
        assertEquals(FileRef(id = 7, size = 900), TdApiFixtures.file(id = 7, size = 0, expectedSize = 900).toFileRef())
    }

    @Test
    fun `minithumbnails with the same bytes are equal`() {
        val mapped = TdApiFixtures.minithumbnail(width = 40, height = 30, data = byteArrayOf(9, 8)).toMinithumbnail()

        assertEquals(Minithumbnail(40, 30, byteArrayOf(9, 8)), mapped)
    }
}
