package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.ExportChat
import dev.kyralabs.pingogram.core.domain.model.ExportChatKind
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.repository.ChatExportAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatExportOutcome
import dev.kyralabs.pingogram.core.domain.repository.ChatExportRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * [DocumentChatExporter] with a folder in memory ([FakeDocumentTree]) and a [FakeExportRepository]: the history read
 * newest first and written oldest first, the dates, the media copied or named, and what a cancelled or failed export
 * leaves behind (nothing).
 */
class DocumentChatExporterTest {
    @TempDir
    lateinit var cache: File

    /** Where the fake TDLib keeps the files it downloaded. */
    @TempDir
    lateinit var tdlibFiles: File

    private val tree = FakeDocumentTree()
    private val repository = FakeExportRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC)

    private fun TestScope.exporter() =
        DocumentChatExporter(repository, tree, StandardTestDispatcher(testScheduler), { cache }, clock)

    private fun options(
        format: ExportFormat = ExportFormat.Json,
        media: Set<ExportMediaKind> = emptySet(),
        maxFileSize: Long = ChatExportOptions.NO_SIZE_LIMIT,
        since: Long? = null,
    ) = ChatExportOptions(
        format = format,
        media = media.toImmutableSet(),
        maxFileSize = maxFileSize,
        since = since,
        folderUri = FakeDocumentTree.TREE,
    )

    private suspend fun DocumentChatExporter.run(
        options: ChatExportOptions,
        progress: MutableList<ChatExportProgress> = mutableListOf(),
    ) = export(100, options, TestExportTexts) { progress += it }

    @Test
    fun `writes the history oldest first into a folder of its own, named after the chat and the day`() =
        runTest {
            repository.history = (1L..5L).map { textMessage(it) }

            val outcome = exporter().run(options())

            val result = (outcome as ChatExportOutcome.Success).result
            assertEquals(5, result.messageCount)
            val folder = tree.child(FakeDocumentTree.ROOT, "Ada – 2026-10-23")
            assertEquals(folder.id, result.folderUri)
            val json = tree.child(folder.id, "messages.json").text()
            val ids = Regex("\"id\": (\\d+),\n      \"date\"").findAll(json).map { it.groupValues[1].toLong() }.toList()
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids)
            assertTrue(json.contains("\"message_count\": 5"), json)
            // Every page was asked for from the oldest message of the one before, then the empty one ended it.
            assertEquals(listOf(0L, 4L, 2L, 1L), repository.pagesFrom)
        }

    @Test
    fun `stops at the day chosen`() =
        runTest {
            repository.history = (1L..5L).map { textMessage(it, date = it * 100) }

            val outcome = exporter().run(options(since = 300))

            assertEquals(3, (outcome as ChatExportOutcome.Success).result.messageCount)
            assertEquals(listOf(0L, 4L), repository.pagesFrom)
        }

    @Test
    fun `reports how far it is, by the chat's size or the days chosen`() =
        runTest {
            repository.history = (1L..4L).map { textMessage(it, date = it * 100) }
            repository.count = 4
            val progress = mutableListOf<ChatExportProgress>()

            exporter().run(options(), progress)

            assertEquals(listOf(0f, 0.5f, 0.99f), progress.map { it.fraction }.distinct())
            assertEquals(4, progress.last().messages)

            val dated = mutableListOf<ChatExportProgress>()
            exporter().run(options(since = 200), dated)
            // From the newest day (400) back to the one chosen (200): halfway after the first page.
            assertEquals(0.5f, dated.first { it.messages == 2 }.fraction)
        }

    @Test
    fun `copies the media chosen into media, names the rest and why`() =
        runTest {
            repository.history =
                listOf(
                    photoMessage(5, fileId = 50, size = 10),
                    documentMessage(4, fileId = 40, size = 10, name = "report/1.pdf"),
                    documentMessage(3, fileId = 30, size = 10_000, name = "big.zip"),
                    photoMessage(2, fileId = 20, size = 10, isSecret = true),
                    documentMessage(1, fileId = 10, size = 10, name = "gone.txt"),
                )
            repository.files = mapOf(50 to "photo bytes", 40 to "pdf bytes")

            val outcome =
                exporter().run(
                    options(media = setOf(ExportMediaKind.Photos, ExportMediaKind.Files), maxFileSize = 1_000),
                )

            val result = (outcome as ChatExportOutcome.Success).result
            assertEquals(2, result.mediaCount)
            assertEquals(3, result.skippedMediaCount)
            val folder = tree.child(FakeDocumentTree.ROOT, "Ada – 2026-10-23")
            val media = tree.child(folder.id, "media")
            assertEquals("pdf bytes", tree.child(media.id, "4_report_1.pdf").text())
            assertEquals("photo bytes", tree.child(media.id, "5_photo_5.jpg").text())
            val json = tree.child(folder.id, "messages.json").text()
            assertTrue(json.contains("\"file\": \"media/4_report_1.pdf\""), json)
            assertTrue(
                json.contains(
                    "\"file_name\": \"big.zip\",\n        \"size\": 10000,\n        \"not_exported\": \"too_large\"",
                ),
                json,
            )
            assertTrue(json.contains("\"not_exported\": \"self_destructing\""), json)
            assertTrue(json.contains("\"not_exported\": \"unavailable\""), json)
            // Only the file chosen and small enough was downloaded.
            assertEquals(listOf(50, 40, 10), repository.downloads)
        }

    @Test
    fun `names senders, services and replies in the chat`() =
        runTest {
            repository.history =
                listOf(
                    textMessage(2, replyTo = ReplyTo.Message(100, 1, null, null, 0, null)),
                    domainMessage(id = 1, content = MessageContent.Service(ServiceAction.Other)),
                )

            val outcome = exporter().run(options(format = ExportFormat.Html))

            val folder = tree.child(FakeDocumentTree.ROOT, "Ada – 2026-10-23")
            assertInstanceOf(ChatExportOutcome.Success::class.java, outcome)
            val html = tree.child(folder.id, "messages.html").text()
            assertTrue(html.contains("Grace did something"), html)
            assertTrue(html.contains("<a href=\"#m1\">In reply to this message</a>"), html)
            assertTrue(html.contains("<p class=\"from\" dir=\"auto\">Grace</p>"), html)
            // A name is asked for once per export.
            assertEquals(1, repository.namesAsked.count { it == MessageSender.User(GRACE) })
        }

    @Test
    fun `Saved Messages is named so, not after the user`() =
        runTest {
            repository.chat = ExportChat(100, "Ada", ExportChatKind.SavedMessages)

            exporter().run(options())

            tree.child(FakeDocumentTree.ROOT, "Saved Messages – 2026-10-23")
        }

    @Test
    fun `secret chats and chats with protected content are not exported, and nothing is written`() =
        runTest {
            repository.access = ChatExportAccess.SecretChat
            assertEquals(ChatExportOutcome.Failure(ChatExportError.NotAllowed), exporter().run(options()))
            repository.access = ChatExportAccess.ProtectedContent
            assertEquals(ChatExportOutcome.Failure(ChatExportError.NotAllowed), exporter().run(options()))

            assertTrue(tree.children(FakeDocumentTree.ROOT).isEmpty())
            assertTrue(repository.pagesFrom.isEmpty())
        }

    @Test
    fun `Telegram's errors fail the export and delete what it wrote`() =
        runTest {
            for (error in listOf(DomainError.Network, DomainError.Unauthorized, DomainError.FloodWait(30))) {
                repository.history = (1L..5L).map { textMessage(it) }
                repository.failPageFrom = 2L to error

                assertEquals(ChatExportOutcome.Failure(ChatExportError.Telegram(error)), exporter().run(options()))
                assertTrue(tree.children(FakeDocumentTree.ROOT).isEmpty())
                assertEquals(0, cache.listFiles()?.size ?: 0)
            }
        }

    @Test
    fun `a download that fails for the network fails the export`() =
        runTest {
            repository.history = listOf(photoMessage(1, fileId = 10, size = 10))
            repository.downloadError = DomainError.Network

            val outcome = exporter().run(options(media = setOf(ExportMediaKind.Photos)))

            assertEquals(ChatExportOutcome.Failure(ChatExportError.Telegram(DomainError.Network)), outcome)
            assertTrue(tree.children(FakeDocumentTree.ROOT).isEmpty())
        }

    @Test
    fun `a folder that is gone or full fails the export`() =
        runTest {
            tree.rootGone = true
            assertEquals(ChatExportOutcome.Failure(ChatExportError.FolderUnavailable), exporter().run(options()))

            tree.rootGone = false
            tree.full = true
            repository.history = (1L..2L).map { textMessage(it) }
            assertEquals(ChatExportOutcome.Failure(ChatExportError.StorageFull), exporter().run(options()))
            assertTrue(tree.children(FakeDocumentTree.ROOT).isEmpty())
            assertEquals(0, cache.listFiles()?.size ?: 0)
        }

    @Test
    fun `cancelled, it stops asking and deletes its folder and temporary pages`() =
        runTest {
            repository.history = (1L..5L).map { textMessage(it) }
            repository.blockPageFrom = 2

            val export = async { exporter().run(options()) }
            runCurrent()
            assertEquals(1, tree.children(FakeDocumentTree.ROOT).size)
            assertTrue(cache.walk().any { it.isFile })
            export.cancel()
            runCurrent()

            assertTrue(export.isCancelled)
            assertTrue(tree.children(FakeDocumentTree.ROOT).isEmpty())
            assertEquals(0, cache.listFiles()?.size ?: 0)
            assertEquals(listOf(0L, 4L, 2L), repository.pagesFrom)
        }

    @Test
    fun `an empty chat is an export without messages`() =
        runTest {
            val outcome = exporter().run(options(format = ExportFormat.Html))

            assertEquals(0, (outcome as ChatExportOutcome.Success).result.messageCount)
            val folder = tree.child(FakeDocumentTree.ROOT, "Ada – 2026-10-23")
            assertTrue(tree.child(folder.id, "messages.html").text().contains("No messages"))
            assertFalse(tree.children(folder.id).any { it.name == "media" })
            assertNull(tree.children(folder.id).firstOrNull { it.name == "messages.json" })
        }

    private fun textMessage(
        id: Long,
        date: Long = 1_700_000_000 + id,
        replyTo: ReplyTo? = null,
    ) = domainMessage(
        id = id,
        date = date,
        content = MessageContent.Text(FormattedText("Message $id", persistentListOf()), null),
        replyTo = replyTo,
    )

    private fun photoMessage(
        id: Long,
        fileId: Int,
        size: Long,
        isSecret: Boolean = false,
    ) = domainMessage(
        id = id,
        content =
            MessageContent.Photo(
                Photo(persistentListOf(PhotoSize("x", 800, 600, FileRef(fileId, size))), null),
                FormattedText.Empty,
                hasSpoiler = false,
                isSecret = isSecret,
                showCaptionAboveMedia = false,
            ),
    )

    private fun documentMessage(
        id: Long,
        fileId: Int,
        size: Long,
        name: String,
    ) = domainMessage(
        id = id,
        content =
            MessageContent.Document(
                Document(name, "application/octet-stream", null, null, FileRef(fileId, size)),
                FormattedText.Empty,
            ),
    )

    /** A [Message] with what these tests do not care about filled in: sent by Grace in chat 100. */
    private fun domainMessage(
        id: Long,
        content: MessageContent,
        date: Long = 1_700_000_000 + id,
        replyTo: ReplyTo? = null,
    ) = Message(
        id = id,
        chatId = 100,
        sender = MessageSender.User(GRACE),
        date = date,
        editDate = 0,
        isOutgoing = false,
        sendingState = null,
        isPinned = false,
        isChannelPost = false,
        containsUnreadMention = false,
        mediaAlbumId = 0,
        authorSignature = "",
        viaBotUserId = 0,
        replyTo = replyTo,
        forwardInfo = null,
        interactionInfo = null,
        selfDestruct = null,
        content = content,
    )

    /** A [ChatExportRepository] of [history] (newest first, [PAGE] at a time) and [files] by id. */
    private inner class FakeExportRepository : ChatExportRepository {
        var chat = ExportChat(100, "Ada", ExportChatKind.Private)
        var access = ChatExportAccess.Allowed
        var history: List<Message> = emptyList()
        var count: Int? = null
        var files: Map<Int, String> = emptyMap()
        var downloadError: DomainError? = null
        var failPageFrom: Pair<Long, DomainError>? = null
        var blockPageFrom: Long? = null
        val pagesFrom = mutableListOf<Long>()
        val downloads = mutableListOf<Int>()
        val namesAsked = mutableListOf<MessageSender>()

        override suspend fun chat(chatId: Long): Outcome<Pair<ExportChat, ChatExportAccess>> =
            Outcome.Success(chat to access)

        override suspend fun messageCount(chatId: Long): Outcome<Int?> = Outcome.Success(count)

        override suspend fun page(
            chatId: Long,
            fromMessageId: Long,
            onFloodWait: suspend (seconds: Long) -> Unit,
        ): Outcome<List<Message>> {
            pagesFrom += fromMessageId
            if (blockPageFrom == fromMessageId) CompletableDeferred<Unit>().await()
            failPageFrom?.let { (from, error) -> if (from == fromMessageId) return Outcome.Failure(error) }
            val sorted = history.sortedByDescending { it.id }
            return Outcome.Success(
                sorted.filter { fromMessageId == 0L || it.id < fromMessageId }.take(PAGE),
            )
        }

        override suspend fun senderName(sender: MessageSender): String? {
            namesAsked += sender
            return if (sender == MessageSender.User(GRACE)) "Grace" else null
        }

        override suspend fun download(
            fileId: Int,
            onFloodWait: suspend (seconds: Long) -> Unit,
        ): Outcome<String> {
            downloads += fileId
            val content = files[fileId]
            return when {
                downloadError != null -> Outcome.Failure(checkNotNull(downloadError))
                content == null -> Outcome.Failure(DomainError.NotFound)
                else -> Outcome.Success(File(tdlibFiles, "file-$fileId").apply { writeText(content) }.path)
            }
        }
    }

    private companion object {
        const val GRACE = 7L
        const val PAGE = 2
    }
}

/** A Storage Access Framework folder in memory: documents by id, their names, types, children and bytes. */
internal class FakeDocumentTree : DocumentTree {
    class Node(
        val id: String,
        val name: String,
        val parent: String?,
        val isFolder: Boolean,
    ) {
        var bytes = ByteArray(0)

        fun text() = bytes.decodeToString()
    }

    private val nodes = mutableMapOf(ROOT to Node(ROOT, "Download", null, isFolder = true))
    private var next = 0

    var rootGone = false
    var full = false

    fun children(parent: String) = nodes.values.filter { it.parent == parent }

    fun child(
        parent: String,
        name: String,
    ): Node = children(parent).single { it.name == name }

    override fun root(treeUri: String): String? = ROOT.takeIf { treeUri == TREE && !rootGone }

    override fun createFolder(
        parent: String,
        name: String,
    ): String = create(parent, name, isFolder = true)

    override fun createFile(
        parent: String,
        name: String,
        mimeType: String,
    ): String = create(parent, name, isFolder = false)

    private fun create(
        parent: String,
        name: String,
        isFolder: Boolean,
    ): String {
        val id = "doc:${next++}"
        nodes[id] = Node(id, name, parent, isFolder)
        return id
    }

    override fun openOutput(document: String): OutputStream {
        if (full) throw IOException("write failed: ENOSPC (No space left on device)")
        val node = nodes.getValue(document)
        return object : ByteArrayOutputStream() {
            override fun close() {
                node.bytes = toByteArray()
            }
        }
    }

    override fun delete(document: String): Boolean {
        children(document).forEach { delete(it.id) }
        return nodes.remove(document) != null
    }

    companion object {
        const val TREE = "content://tree/download"
        const val ROOT = "doc:root"
    }
}
