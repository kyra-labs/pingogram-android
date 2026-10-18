package dev.kyralabs.pingogram.feature.media.ui.files

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * [AndroidMediaFileActions] on Robolectric: on Android 10 and later the copy goes through `MediaStore` (a fake media
 * provider records what it is told), without any permission, pending until written and removed if the copy fails;
 * the copy into the public album of Android 8–9 keeps files of the same name.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidMediaFileActionsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val bytes = byteArrayOf(1, 2, 3, 4, 5)

    private fun TestScope.actions() = AndroidMediaFileActions(context, StandardTestDispatcher(testScheduler))

    private fun file(name: String): File = folder.newFile(name).apply { writeBytes(bytes) }

    @Test
    fun `Android 10 and later need no permission`() {
        assertFalse(AndroidMediaFileActions(context, StandardTestDispatcher()).needsStoragePermission())
    }

    @Test
    fun `a photo goes into Pictures-Pingogram through MediaStore, pending until it is written`() =
        runTest {
            val store = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
            val written = ByteArrayOutputStream()
            shadowOf(context.contentResolver).registerOutputStream(IMAGE_ITEM, written)

            val saved = actions().saveToGallery(GalleryItem(file("file_50.jpg").path, "image/jpeg", "Pingogram_50"))

            assertTrue(saved)
            val (collection, values) = store.inserted.single()
            assertEquals(IMAGES, collection)
            assertEquals("Pingogram_50.jpg", values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            assertEquals("image/jpeg", values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
            assertEquals("Pictures/Pingogram", values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
            assertEquals(1, values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            assertArrayEquals(bytes, written.toByteArray())
            val (published, publishedValues) = store.updated.single()
            assertEquals(IMAGE_ITEM, published)
            assertEquals(0, publishedValues.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
        }

    @Test
    fun `a video goes into Movies-Pingogram`() =
        runTest {
            val store = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
            shadowOf(context.contentResolver).registerOutputStream(VIDEO_ITEM, ByteArrayOutputStream())

            val saved = actions().saveToGallery(GalleryItem(file("file_51.mp4").path, "video/mp4", "Pingogram_51"))

            assertTrue(saved)
            val (collection, values) = store.inserted.single()
            assertEquals(VIDEOS, collection)
            assertEquals("Pingogram_51.mp4", values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            assertEquals("video/mp4", values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
            assertEquals("Movies/Pingogram", values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        }

    @Test
    fun `a file without an extension takes the type it is given, and its extension`() =
        runTest {
            val store = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
            shadowOf(context.contentResolver).registerOutputStream(IMAGE_ITEM, ByteArrayOutputStream())

            actions().saveToGallery(GalleryItem(file("file_52").path, "image/jpeg", "Pingogram_52"))

            val values = store.inserted.single().second
            assertEquals("Pingogram_52.jpg", values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            assertEquals("image/jpeg", values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        }

    @Test
    fun `a copy that fails removes the pending item`() =
        runTest {
            val store = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
            shadowOf(context.contentResolver).registerOutputStream(IMAGE_ITEM, ByteArrayOutputStream())
            val missing = File(folder.root, "gone.jpg").path

            val saved = actions().saveToGallery(GalleryItem(missing, "image/jpeg", "Pingogram_53"))

            assertFalse(saved)
            assertEquals(listOf(IMAGE_ITEM), store.deleted)
            assertTrue(store.updated.isEmpty())
        }

    @Test
    fun `a gallery that refuses the item is a failure`() =
        runTest {
            val store = Robolectric.setupContentProvider(FakeMediaStore::class.java, MediaStore.AUTHORITY)
            store.refuses = true

            val saved = actions().saveToGallery(GalleryItem(file("file_54.jpg").path, "image/jpeg", "Pingogram_54"))

            assertFalse(saved)
        }

    // Android 8–9 (Robolectric's runtime for them is not among the build's) copy into the public album directory.

    @Test
    fun `the album copy makes its directory and keeps a file of the same name`() {
        val album = File(folder.root, "Pictures/Pingogram")
        val source = file("file_55.jpg")

        val first = copyIntoAlbum(source, album, "Pingogram_55.jpg")
        val second = copyIntoAlbum(source, album, "Pingogram_55.jpg")

        assertEquals(File(album, "Pingogram_55.jpg"), first)
        assertEquals(File(album, "Pingogram_55 (1).jpg"), second)
        assertArrayEquals(bytes, first.readBytes())
        assertArrayEquals(bytes, second.readBytes())
    }

    private companion object {
        val IMAGES: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val VIDEOS: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        /** The first item the fake media provider inserts into each collection. */
        val IMAGE_ITEM: Uri = ContentUris.withAppendedId(IMAGES, 1)
        val VIDEO_ITEM: Uri = ContentUris.withAppendedId(VIDEOS, 1)
    }
}

/**
 * The media provider's side of what [AndroidMediaFileActions] does: the items inserted (refused while [refuses]),
 * updated and deleted, by URI with their values. Writing goes through the content resolver's registered streams.
 */
class FakeMediaStore : ContentProvider() {
    val inserted = mutableListOf<Pair<Uri, ContentValues>>()
    val updated = mutableListOf<Pair<Uri, ContentValues>>()
    val deleted = mutableListOf<Uri>()
    var refuses = false

    override fun onCreate(): Boolean = true

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
        if (refuses) return null
        inserted += uri to ContentValues(values)
        return ContentUris.withAppendedId(uri, inserted.count { it.first == uri }.toLong())
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        updated += uri to ContentValues(values)
        return 1
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        deleted += uri
        return 1
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
}
