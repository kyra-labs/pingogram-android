package dev.kyralabs.pingogram.share

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What a share from another app carries into Pingogram ([shareOf]), and what it never does. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ShareIntentsTest {
    private val notOwn: (Uri) -> Boolean = { false }

    @Test
    fun `a shared text or link is the share's text`() {
        val share = shareOf(send("text/plain").putExtra(Intent.EXTRA_TEXT, "  https://example.com  "), notOwn)

        assertEquals("https://example.com", share?.content?.text?.text)
        assertTrue(share!!.content.files.isEmpty())
        assertNull(share.chatId)
    }

    @Test
    fun `a shared photo goes as media, with its text as caption`() {
        val share =
            shareOf(
                send("image/jpeg").putExtra(Intent.EXTRA_STREAM, Uri.parse(PHOTO)).putExtra(Intent.EXTRA_TEXT, "Look"),
                notOwn,
            )!!

        assertEquals(listOf(PHOTO), share.content.files)
        assertEquals(AttachmentKind.Media, share.content.kind)
        assertEquals("Look", share.content.text.text)
    }

    @Test
    fun `several files of different types go as documents, each once`() {
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("*/*")
                .putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf(Uri.parse(PHOTO), Uri.parse(DOCUMENT), Uri.parse(PHOTO)),
                )

        val share = shareOf(intent, notOwn)!!

        assertEquals(listOf(PHOTO, DOCUMENT), share.content.files)
        assertEquals(AttachmentKind.Document, share.content.kind)
    }

    @Test
    fun `several photos and videos go as media`() {
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("image/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(Uri.parse(PHOTO)))

        assertEquals(AttachmentKind.Media, shareOf(intent, notOwn)!!.content.kind)
    }

    @Test
    fun `files only in the clip data are shared too`() {
        val intent = send("application/pdf")
        intent.clipData = ClipData.newRawUri(null, Uri.parse(DOCUMENT))

        assertEquals(listOf(DOCUMENT), shareOf(intent, notOwn)!!.content.files)
    }

    @Test
    fun `a share to a Direct Share target names its chat`() {
        val intent =
            send("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Hi")
                .putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, NotificationIntents.shortcutId(100))

        assertEquals(100L, shareOf(intent, notOwn)!!.chatId)
        val other =
            send(
                "text/plain",
            ).putExtra(Intent.EXTRA_TEXT, "Hi").putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, "x")
        assertNull(shareOf(other, notOwn)!!.chatId)
    }

    @Test
    fun `file URIs and the app's own content are never shared`() {
        val ownAuthority = "${RuntimeEnvironment.getApplication().packageName}.files"
        val own = "content://$ownAuthority/tdlib/db/td.binlog"
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("*/*")
                .putParcelableArrayListExtra(
                    Intent.EXTRA_STREAM,
                    arrayListOf(Uri.parse("file:///data/data/x/files/secret"), Uri.parse(own), Uri.parse(DOCUMENT)),
                )

        val share = shareOf(intent) { it.authority == ownAuthority }!!

        assertEquals(listOf(DOCUMENT), share.content.files)
    }

    @Test
    fun `nothing to share, other intents and shares replayed from the recent apps are none`() {
        assertNull(shareOf(send("text/plain"), notOwn))
        assertNull(shareOf(send("text/plain").putExtra(Intent.EXTRA_TEXT, "   "), notOwn))
        assertNull(shareOf(null, notOwn))
        assertNull(shareOf(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/durov")), notOwn))
        val replayed =
            send("text/plain").putExtra(Intent.EXTRA_TEXT, "Hi").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(shareOf(replayed, notOwn))
    }

    @Test
    fun `the app's own provider is its own content, others are not`() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(context.isOwnContent(Uri.parse("content://${context.packageName}.files/tdlib/files/a.jpg")))
        assertEquals(false, context.isOwnContent(Uri.parse(PHOTO)))
    }

    private fun send(type: String) = Intent(Intent.ACTION_SEND).setType(type)

    private companion object {
        const val PHOTO = "content://media/external/images/media/1"
        const val DOCUMENT = "content://com.android.providers.downloads.documents/document/7"
    }
}
