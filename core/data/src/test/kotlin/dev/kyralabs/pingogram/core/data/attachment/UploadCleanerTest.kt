package dev.kyralabs.pingogram.core.data.attachment

import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.closed
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.failedState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.file
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.pendingState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateDeleteMessages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageSendFailed
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateMessageSendSucceeded
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/** When [UploadCleaner] deletes the copies of files being sent, and when it keeps them. */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadCleanerTest {
    @TempDir
    lateinit var cacheDir: File

    private val client = FakeTdlibClient()
    private val files by lazy { UploadFiles { cacheDir } }

    private fun TestScope.cleaner(maxAge: Duration? = null): UploadCleaner =
        UploadCleaner(client, files, StandardTestDispatcher(testScheduler), backgroundScope, maxAge).also {
            runCurrent()
        }

    /** A copy in a directory of its own, as the preparer leaves it. */
    private fun copy(name: String = "photo.jpg") = File(files.newUploadDirectory(), name).apply { writeText("data") }

    /** An outgoing photo message uploading [copy], in [state]. */
    private fun photoMessage(
        id: Long,
        copy: File,
        state: TdApi.MessageSendingState? = pendingState(),
    ) = message(
        id = id,
        isOutgoing = true,
        sendingState = state,
        content =
            TdApiFixtures.messagePhoto(
                TdApiFixtures.photo(arrayOf(TdApiFixtures.photoSize(file = file(id = 9, localPath = copy.path)))),
            ),
    )

    private suspend fun TestScope.emit(update: TdApi.Update) {
        client.emit(update)
        runCurrent()
    }

    @Test
    fun `a copy is deleted once its message is sent`() =
        runTest {
            cleaner()
            val copy = copy()

            emit(updateNewMessage(photoMessage(-1, copy)))
            assertTrue(copy.exists())
            emit(updateMessageSendSucceeded(photoMessage(10, copy, state = null), oldMessageId = -1))

            assertFalse(copy.exists())
            assertFalse(copy.parentFile!!.exists())
        }

    @Test
    fun `a copy is deleted when the message names it, also when it was not followed`() =
        runTest {
            // After a restart nothing is followed; TDLib keeps the local path of an uploaded file.
            cleaner()
            val copy = copy()

            emit(updateMessageSendSucceeded(photoMessage(10, copy, state = null), oldMessageId = -1))

            assertFalse(copy.exists())
        }

    @Test
    fun `a copy is deleted when its message fails for good`() =
        runTest {
            cleaner()
            val copy = copy()

            emit(updateNewMessage(photoMessage(-1, copy)))
            emit(updateMessageSendFailed(photoMessage(-2, copy, failedState(canRetry = false)), oldMessageId = -1))

            assertFalse(copy.exists())
        }

    @Test
    fun `a copy stays while its failed message can be sent again, and goes when that one is sent`() =
        runTest {
            cleaner()
            val copy = copy()

            emit(updateNewMessage(photoMessage(-1, copy)))
            emit(updateMessageSendFailed(photoMessage(-2, copy, failedState(canRetry = true)), oldMessageId = -1))
            assertTrue(copy.exists())
            // resendMessages deletes the failed message, then adds a new one with the same file.
            emit(updateDeleteMessages(100, -2))
            emit(updateNewMessage(photoMessage(-3, copy)))
            advanceTimeBy(UploadCleaner.DELETE_GRACE + 1.milliseconds)
            assertTrue(copy.exists())
            emit(updateMessageSendSucceeded(photoMessage(11, copy, state = null), oldMessageId = -3))

            assertFalse(copy.exists())
        }

    @Test
    fun `the copy of a cancelled upload is deleted after a grace`() =
        runTest {
            cleaner()
            val copy = copy()

            emit(updateNewMessage(photoMessage(-1, copy)))
            emit(updateDeleteMessages(100, -1))
            assertTrue(copy.exists())
            advanceTimeBy(UploadCleaner.DELETE_GRACE + 1.milliseconds)
            runCurrent()

            assertFalse(copy.exists())
        }

    @Test
    fun `messages removed from memory only keep their copies`() =
        runTest {
            cleaner()
            val copy = copy()

            emit(updateNewMessage(photoMessage(-1, copy)))
            emit(updateDeleteMessages(100, -1, isPermanent = false, fromCache = true))
            advanceTimeBy(UploadCleaner.DELETE_GRACE + 1.milliseconds)
            runCurrent()

            assertTrue(copy.exists())
        }

    @Test
    fun `files outside the upload directories are never deleted`() =
        runTest {
            cleaner()
            val gallery = File(cacheDir, "DCIM.jpg").apply { writeText("keep") }

            emit(updateMessageSendSucceeded(photoMessage(10, gallery, state = null), oldMessageId = -1))

            assertTrue(gallery.exists())
        }

    @Test
    fun `logging out deletes the copies of every message being sent`() =
        runTest {
            cleaner()
            val copy = copy()

            emit(updateNewMessage(photoMessage(-1, copy)))
            emit(TdApiFixtures.authorizationStateUpdate(closed()))

            assertFalse(copy.exists())
        }

    @Test
    fun `old leftovers are deleted at start`() =
        runTest {
            val eightDaysAgo = System.currentTimeMillis() - 8.days.inWholeMilliseconds
            val old = copy().parentFile!!.apply { setLastModified(eightDaysAgo) }
            val recent = copy().parentFile!!

            cleaner(maxAge = UploadCleaner.MAX_AGE)

            assertFalse(old.exists())
            assertTrue(recent.exists())
        }

    @Test
    fun `a held copy stays after its first message is sent, until a later one is`() =
        runTest {
            val cleaner = cleaner()
            val copy = copy()

            cleaner.hold(listOf(copy.path))
            emit(updateNewMessage(photoMessage(-1, copy)))
            emit(updateMessageSendSucceeded(photoMessage(10, copy, state = null), oldMessageId = -1))
            assertTrue(copy.exists())

            emit(updateNewMessage(photoMessage(-2, copy)))
            cleaner.release(listOf(copy.path))
            advanceTimeBy(UploadCleaner.DELETE_GRACE + 1.milliseconds)
            runCurrent()
            assertTrue(copy.exists())

            emit(updateMessageSendSucceeded(photoMessage(11, copy, state = null), oldMessageId = -2))
            assertFalse(copy.exists())
        }

    @Test
    fun `a released copy no message uploads is deleted after the grace`() =
        runTest {
            val cleaner = cleaner()
            val copy = copy()

            cleaner.hold(listOf(copy.path))
            runCurrent()
            cleaner.release(listOf(copy.path))
            runCurrent()
            assertTrue(copy.exists())

            advanceTimeBy(UploadCleaner.DELETE_GRACE + 1.milliseconds)
            runCurrent()
            assertFalse(copy.exists())
        }
}
