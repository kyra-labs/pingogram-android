package dev.kyralabs.pingogram.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The channel's rich post (`docs/adr/0034-demo-mode-for-store-review.md`): a long read with a title, a paragraph and
 * two closed sections, one holding a photo the demo makes on the phone; it sits before the channel's newest post, so
 * the tip stays the last message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoRichPostTest {
    @TempDir
    lateinit var dir: File

    private lateinit var harness: DemoTestHarness

    @BeforeEach
    fun setUp() {
        harness = DemoTestHarness(dir)
    }

    private fun TestScope.client() = with(harness) { start() }

    private suspend fun DemoTdlibClient.richPost(): TdApi.RichMessage =
        ask(TdApi.GetChatHistory(DemoData.CHANNEL_CHAT, 0, 0, 10, false))
            .messages
            .firstNotNullOf { (it.content as? TdApi.MessageRichMessage)?.message }

    @Test
    fun `the channel has a rich post with a title, a paragraph and two closed sections`() =
        runTest {
            val (client, _) = client()

            val post = client.richPost()

            assertTrue(post.isFull)
            assertEquals(
                listOf("Kicker", "Title", "Paragraph", "Details", "Details"),
                post.blocks.map { it.javaClass.simpleName.removePrefix("PageBlock") },
            )
            val sections = post.blocks.filterIsInstance<TdApi.PageBlockDetails>()
            assertTrue(sections.none { it.isOpen })
            assertEquals(1, sections.first().blocks.count { it is TdApi.PageBlockPhoto })
        }

    @Test
    fun `the post's photo is made on the phone when it downloads`() =
        runTest {
            val (client, _) = client()
            val photo =
                client
                    .richPost()
                    .blocks
                    .filterIsInstance<TdApi.PageBlockDetails>()
                    .flatMap { it.blocks.asList() }
                    .filterIsInstance<TdApi.PageBlockPhoto>()
                    .single()
            val fileId =
                photo.photo!!
                    .sizes
                    .single()
                    .photo.id

            val downloaded = client.ask(TdApi.DownloadFile(fileId, 32, 0, 0, false))
            runCurrent()

            assertEquals(DemoFile.RichPhoto.id, fileId)
            assertTrue(downloaded.local.isDownloadingCompleted)
            assertEquals(dir.resolve(DemoFile.RichPhoto.fileName).path, downloaded.local.path)
        }

    @Test
    fun `the tip stays the channel's newest post`() =
        runTest {
            val (client, _) = client()

            val newest = client.ask(TdApi.GetChatHistory(DemoData.CHANNEL_CHAT, 0, 0, 1, false)).messages.single()

            assertTrue(newest.content is TdApi.MessageText)
        }
}
