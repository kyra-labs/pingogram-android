package dev.kyralabs.pingogram

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import dev.kyralabs.pingogram.core.ui.file.ExternalFileResult
import dev.kyralabs.pingogram.core.ui.file.shareTdlibFile
import dev.kyralabs.pingogram.core.ui.file.tdlibFileUri
import dev.kyralabs.pingogram.core.ui.file.viewTdlibFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * The app's `FileProvider` with `core:ui`'s `TdlibFiles`: TDLib's downloaded files (`TdlibDirectories.files`, of
 * either data center) get content URIs of the app's authority, anything else (TDLib's database above all) none; shared
 * files go to the system's chooser and documents to the app that opens them, each with a read grant for its URI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TdlibFileProviderTest {
    private val context = RuntimeEnvironment.getApplication()
    private val production = TdlibDirectories.of(context.filesDir, useTestDc = false)
    private val testDc = TdlibDirectories.of(context.filesDir, useTestDc = true)

    /**
     * FileProvider keeps the paths of each authority in a static cache, which outlives the test's application (and its
     * own `filesDir`) in Robolectric's class loader; the next test reads them anew.
     */
    @Before
    fun forgetProviderPaths() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    private fun file(
        directory: File,
        path: String,
    ): File = File(directory, path).apply { parentFile!!.mkdirs() }.apply { writeText("content") }

    @Test
    fun `a downloaded TDLib file gets a content URI of the app's provider`() {
        val photo = file(production.files, "photos/file_1.jpg")

        assertEquals(
            Uri.parse("content://${context.packageName}.files/tdlib/photos/file_1.jpg"),
            context.tdlibFileUri(photo.path),
        )
    }

    @Test
    fun `so does one of the test data center`() {
        val video = file(testDc.files, "videos/file_2.mp4")

        assertEquals(
            Uri.parse("content://${context.packageName}.files/tdlib-test/videos/file_2.mp4"),
            context.tdlibFileUri(video.path),
        )
    }

    @Test
    fun `TDLib's database and other files of the app get none`() {
        val database = file(production.database, "db.sqlite")
        val log = file(production.root, "logs/tdlib.log")
        val other = file(context.filesDir, "other.txt")

        assertNull(context.tdlibFileUri(database.path))
        assertNull(context.tdlibFileUri(log.path))
        assertNull(context.tdlibFileUri(other.path))
    }

    @Test
    fun `a path that climbs out of TDLib's files gets none`() {
        file(production.database, "db.sqlite")

        assertNull(context.tdlibFileUri("${production.files.path}/../db/db.sqlite"))
    }

    @Test
    fun `a link out of TDLib's files gets none`() {
        val database = file(production.database, "db.sqlite")
        production.files.mkdirs()
        val link = File(production.files, "photos-link.jpg")
        Files.createSymbolicLink(link.toPath(), database.toPath())

        assertNull(context.tdlibFileUri(link.path))
    }

    @Test
    fun `a file that is gone gets none`() {
        assertNull(context.tdlibFileUri(File(production.files, "photos/gone.jpg").path))
    }

    @Test
    fun `sharing opens the system's chooser with the file and a read grant`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val photo = file(production.files, "photos/file_3.jpg")

        val result = activity.shareTdlibFile(photo.path, "image/webp", "Share")

        assertEquals(ExternalFileResult.Started, result)
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = checkNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals(Intent.ACTION_SEND, send.action)
        // The extension's type wins over the fallback.
        assertEquals("image/jpeg", send.type)
        assertEquals(
            context.tdlibFileUri(photo.path),
            IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java),
        )
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `a file outside TDLib's files is not shared`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val other = file(context.filesDir, "other.jpg")

        assertEquals(ExternalFileResult.Unavailable, activity.shareTdlibFile(other.path, "image/jpeg", "Share"))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `a document opens in the app for its type, with a read grant`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val document = file(production.files, "documents/report.pdf")

        val result = activity.viewTdlibFile(document.path, "application/pdf", "Open with")

        assertEquals(ExternalFileResult.Started, result)
        val view = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(context.tdlibFileUri(document.path), view.data)
        assertEquals("application/pdf", view.type)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `a document of no type opens as its extension's, or in a chooser of any app`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val pdf = file(production.files, "documents/report.pdf")
        val unknown = file(production.files, "documents/notes")

        activity.viewTdlibFile(pdf.path, "application/octet-stream", "Open with")
        assertEquals("application/pdf", shadowOf(activity).nextStartedActivity.type)

        activity.viewTdlibFile(unknown.path, null, "Open with")
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val view = checkNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertEquals("*/*", view.type)
    }

    @Test
    fun `no app for a document says so`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        val document = file(production.files, "documents/report.xyz")

        assertEquals(ExternalFileResult.NoApp, activity.viewTdlibFile(document.path, "application/x-xyz", "Open with"))
    }
}
