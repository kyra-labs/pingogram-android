package dev.kyralabs.pingogram.core.data.attachment

import dev.kyralabs.pingogram.core.domain.model.Attachment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** The cache directories of [UploadFiles]: what counts as a copy, and that nothing else is ever deleted. */
class UploadFilesTest {
    @TempDir
    lateinit var cacheDir: File

    private val files by lazy { UploadFiles { cacheDir } }

    private fun copyIn(directory: File = files.newUploadDirectory()) =
        File(directory, "copy.pdf").apply { writeText("copy") }

    @Test
    fun `copies are files in the upload directories, also by a path with dots`() {
        val copy = copyIn()

        assertTrue(files.isUpload(copy.path))
        assertTrue(files.isUpload("${copy.parentFile!!.path}/../${copy.parentFile!!.name}/copy.pdf"))
        assertFalse(files.isUpload(File(cacheDir, "other.pdf").path))
        assertFalse(files.isUpload("${copy.parentFile!!.path}/../../other.pdf"))
        assertFalse(files.isUpload(""))
    }

    @Test
    fun `deleting a copy deletes the directory it was prepared in`() {
        val copy = copyIn()
        val sibling = File(copy.parentFile, "thumbnail.jpg").apply { writeText("still") }

        files.delete(listOf(Attachment.Document(copy.path, 4, "copy.pdf", "application/pdf")))

        assertFalse(copy.exists())
        assertFalse(sibling.exists())
        assertFalse(copy.parentFile!!.exists())
    }

    @Test
    fun `files outside the upload directories are never deleted`() {
        val outside = File(cacheDir, "tdlib.db").apply { writeText("keep") }
        val camera = files.newCameraFile()

        files.deleteUploads(listOf(outside.path, camera.path, cacheDir.path))

        assertTrue(outside.exists())
        assertTrue(camera.exists())
    }

    @Test
    fun `camera files are found by their path, and only in the camera directory`() {
        val camera = files.newCameraFile()

        assertEquals(camera.canonicalPath, files.cameraFile(camera.path)?.canonicalPath)
        assertNull(files.cameraFile(copyIn().path))
        assertNull(files.cameraFile(File(camera.parentFile, "../uploads").path))
    }

    @Test
    fun `old leftovers are deleted, recent ones kept`() {
        val now = 1_000_000_000_000L
        val old = copyIn().parentFile!!.apply { setLastModified(now - 8 * DAY) }
        val recent = copyIn().parentFile!!.apply { setLastModified(now - DAY) }
        val oldShot = files.newCameraFile().apply { setLastModified(now - 8 * DAY) }

        files.deleteOlderThan(7 * DAY, nowMillis = now)

        assertFalse(old.exists())
        assertFalse(oldShot.exists())
        assertTrue(recent.exists())
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
