package dev.kyralabs.pingogram.feature.media.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatMediaFilter
import dev.kyralabs.pingogram.core.domain.model.ChatMediaPage
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.ADA
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.CHANNEL
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.chat
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.fullFileId
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.gifContent
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.message
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.photoContent
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.photos
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.user
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.videoContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerViewModelTest : MediaViewerViewModelHarness() {
    private val photos = ChatMediaFilter.PhotosAndVideos

    /** Every file of messages [ids] is known and not downloaded yet, so the viewer downloads what it needs. */
    private fun notDownloaded(ids: LongRange) {
        for (id in ids) {
            files.setFile(fullFileId(id), FileState.NotDownloaded(size = 1_000, canBeDownloaded = true))
            files.setFile(MediaFixtures.smallFileId(id), FileState.NotDownloaded(size = 100, canBeDownloaded = true))
        }
    }

    @Nested
    inner class Loading {
        @Test
        fun `loads the media around the opened message, then a page on each near side`() =
            runViewModelTest {
                media.serve(photos(1L..100L))

                val viewModel = viewModel(messageId = 50)

                assertEquals(
                    listOf("around:50", "older:$photos:48", "newer:$photos:52", "position:$photos:50"),
                    media.calls,
                )
                assertEquals((45L..55L).toList(), viewModel.ids)
                assertEquals(50, viewModel.current)
            }

        @Test
        fun `shows loading until the first page is there`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<ChatMediaPage>>()
                media.onAround = { answer.await() }

                val viewModel = viewModel(messageId = 1)

                assertEquals(MediaViewerUiState.Loading, viewModel.state)
            }

        @Test
        fun `items are oldest first, with the opened one current`() =
            runViewModelTest {
                media.serve(photos(1L..3L))

                val viewModel = viewModel(messageId = 2)

                assertEquals(listOf(1L, 2L, 3L), viewModel.ids)
                assertEquals(1, viewModel.content.currentIndex)
            }

        @Test
        fun `items name their sender and date`() =
            runViewModelTest {
                users.setUsers(user(ADA, "Ada", "Lovelace"))
                chats.chats.value = mapOf(CHANNEL to chat(CHANNEL, "Engines Weekly"))
                media.serve(
                    listOf(
                        message(1, sender = MessageSender.User(ADA)),
                        message(2, sender = MessageSender.Chat(CHANNEL), date = Instant.parse("2025-03-17T08:05:00Z")),
                        message(3, sender = MessageSender.User(404)),
                    ),
                )

                val items = viewModel(messageId = 2).content.items

                assertEquals(
                    listOf(UiText.Plain("Ada Lovelace"), UiText.Plain("Engines Weekly"), UiText.resource(SOMEONE)),
                    items.map { it.sender },
                )
                assertEquals(
                    listOf("Oct 18, 9:41 AM", "Mar 17, 2025, 8:05 AM", "Oct 18, 9:41 AM"),
                    items.map { it.date },
                )
            }

        @Test
        fun `a GIF opens among GIFs`() =
            runViewModelTest {
                media.serve(listOf(message(1, gifContent(1)), message(2, gifContent(2))), ChatMediaFilter.Gifs)

                val viewModel = viewModel(messageId = 2)

                assertTrue(viewModel.content.current.media is ViewerMedia.Gif)
                assertEquals("older:${ChatMediaFilter.Gifs}:1", media.calls[1])
            }

        @Test
        fun `a failed first page shows the error, and trying again loads it`() =
            runViewModelTest {
                media.onAround = { Outcome.Failure(DomainError.Network) }
                val viewModel = viewModel(messageId = 5)
                assertEquals(MediaViewerUiState.Error(DomainError.Network), viewModel.state)

                media.serve(photos(1L..9L))
                send(viewModel, MediaViewerEvent.Retry)

                assertEquals(5, viewModel.current)
                assertEquals(2, media.calls.count { it == "around:5" })
            }

        @Test
        fun `a flood wait shows its error`() =
            runViewModelTest {
                media.onAround = { Outcome.Failure(DomainError.FloodWait(30)) }

                assertEquals(MediaViewerUiState.Error(DomainError.FloodWait(30)), viewModel(messageId = 5).state)
            }

        @Test
        fun `a deleted message is no longer available`() =
            runViewModelTest {
                media.serve(photos(1L..3L))

                assertEquals(MediaViewerUiState.Unavailable, viewModel(messageId = 7).state)
            }

        @Test
        fun `nothing is retried by itself`() =
            runViewModelTest {
                media.onAround = { Outcome.Failure(DomainError.Network) }

                viewModel(messageId = 5)
                advanceTimeBy(60_000)
                runCurrent()

                assertEquals(listOf("around:5"), media.calls)
            }
    }

    @Nested
    inner class Paging {
        @Test
        fun `turning towards the oldest loaded loads older media, keeping the current item`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                val viewModel = viewModel(messageId = 50)

                send(viewModel, MediaViewerEvent.PageSettled(47))

                assertEquals("older:$photos:45", media.calls.last())
                assertEquals((42L..55L).toList(), viewModel.ids)
                assertEquals(47, viewModel.current)
                assertEquals(5, viewModel.content.currentIndex)
            }

        @Test
        fun `turning towards the newest loaded loads newer media`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                val viewModel = viewModel(messageId = 50)

                send(viewModel, MediaViewerEvent.PageSettled(53))

                assertEquals("newer:$photos:55", media.calls.last())
                assertEquals((45L..58L).toList(), viewModel.ids)
                assertEquals(53, viewModel.current)
            }

        @Test
        fun `a side loads one page at a time`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                val held = CompletableDeferred<Outcome<ChatMediaPage>>()
                val viewModel = viewModel(messageId = 50)
                val serveOlder = media.onOlder
                media.onOlder = { held.await() }

                send(viewModel, MediaViewerEvent.PageSettled(47), MediaViewerEvent.PageSettled(46))

                assertEquals(1, media.calls.count { it == "older:$photos:45" })
                held.complete(serveOlder(45))
                runCurrent()
                assertEquals(1, media.calls.count { it == "older:$photos:45" })
                assertEquals(42L, viewModel.ids.first())
            }

        @Test
        fun `nothing more loads once both ends are reached`() =
            runViewModelTest {
                media.serve(photos(1L..4L))
                val viewModel = viewModel(messageId = 2)
                val calls = media.calls.toList()

                send(viewModel, MediaViewerEvent.PageSettled(1), MediaViewerEvent.PageSettled(4))

                assertEquals(calls, media.calls)
                assertEquals(listOf(1L, 2L, 3L, 4L), viewModel.ids)
            }

        @Test
        fun `merged pages hold no duplicates`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                val viewModel = viewModel(messageId = 50)
                // TDLib answers overlapping pages.
                val serveNewer = media.onNewer
                media.onNewer = { id -> serveNewer(id - 2) }

                send(viewModel, MediaViewerEvent.PageSettled(54))

                assertEquals(viewModel.ids.distinct(), viewModel.ids)
                assertEquals(viewModel.ids.sorted(), viewModel.ids)
            }

        @Test
        fun `a failed page is a snackbar, retried only when the user moves on`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                val viewModel = viewModel(messageId = 50)
                val serveOlder = media.onOlder
                media.onOlder = { Outcome.Failure(DomainError.Network) }

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.PageSettled(47))
                    assertEquals(MediaViewerEffect.ShowError(DomainError.Network), awaitItem())
                }
                advanceTimeBy(60_000)
                runCurrent()
                assertEquals(1, media.calls.count { it == "older:$photos:45" })

                media.onOlder = serveOlder
                send(viewModel, MediaViewerEvent.PageSettled(46))
                assertEquals(2, media.calls.count { it == "older:$photos:45" })
                // 46 is still near the oldest loaded after that page, so the next one follows.
                assertEquals(39L, viewModel.ids.first())
            }

        @Test
        fun `try again retries a failed page`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                val viewModel = viewModel(messageId = 50)
                val serveOlder = media.onOlder
                media.onOlder = { Outcome.Failure(DomainError.Network) }
                send(viewModel, MediaViewerEvent.PageSettled(47))

                media.onOlder = serveOlder
                send(viewModel, MediaViewerEvent.Retry)

                assertEquals(42L, viewModel.ids.first())
            }

        @Test
        fun `the current item survives process death`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                val handle = SavedStateHandle()
                val first = viewModel(messageId = 50, handle = handle)
                send(first, MediaViewerEvent.PageSettled(53))
                clear(first)
                media.calls.clear()

                val restored =
                    viewModel(
                        messageId = 50,
                        handle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }),
                    )

                assertEquals("around:53", media.calls.first())
                assertEquals(53, restored.current)
            }
    }

    @Nested
    inner class Position {
        @Test
        fun `the position comes from TDLib's position of the opened message`() =
            runViewModelTest {
                media.serve(photos(1L..100L))

                val viewModel = viewModel(messageId = 50)

                assertEquals(MediaPosition(50, 100), viewModel.position)
                send(viewModel, MediaViewerEvent.PageSettled(53))
                assertEquals(MediaPosition(53, 100), viewModel.position)
            }

        @Test
        fun `without TDLib's position it is unknown until an end is reached`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                media.onPosition = { Outcome.Failure(DomainError.Unknown(400, "Message can't be found in the filter")) }

                val viewModel = viewModel(messageId = 50)

                assertNull(viewModel.position)
            }

        @Test
        fun `once the oldest end is reached the position counts from it`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                media.onPosition = { Outcome.Failure(DomainError.Unknown(400, "Secret chat")) }

                val viewModel = viewModel(messageId = 3)

                assertEquals(MediaPosition(3, 100), viewModel.position)
            }

        @Test
        fun `around the newest media the position needs no request`() =
            runViewModelTest {
                media.serve(photos(1L..100L))

                val viewModel = viewModel(messageId = 100)

                assertFalse(media.calls.any { it.startsWith("position") })
                assertEquals(MediaPosition(100, 100), viewModel.position)
            }
    }

    @Nested
    inner class Downloads {
        @Test
        fun `the current item downloads at visible priority, its neighbours at prefetch priority`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                notDownloaded(1L..100L)

                viewModel(messageId = 50)

                assertEquals(
                    listOf("download:501:Visible", "download:491:Prefetch", "download:511:Prefetch"),
                    files.calls,
                )
            }

        @Test
        fun `turning the page moves the priorities along`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                notDownloaded(1L..100L)
                val viewModel = viewModel(messageId = 50)
                files.calls.clear()

                send(viewModel, MediaViewerEvent.PageSettled(51))

                assertEquals(
                    listOf("download:511:Visible", "download:501:Prefetch", "download:521:Prefetch"),
                    files.calls,
                )
            }

        @Test
        fun `downloads of items more than two pages away are cancelled`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                notDownloaded(1L..100L)
                val viewModel = viewModel(messageId = 50)
                files.calls.clear()

                send(viewModel, MediaViewerEvent.PageSettled(53))

                assertEquals(
                    setOf(
                        "download:531:Visible",
                        "download:521:Prefetch",
                        "download:541:Prefetch",
                        "cancel:491",
                        "cancel:501",
                    ),
                    files.calls.toSet(),
                )
            }

        @Test
        fun `files on the device are not downloaded`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                notDownloaded(1L..100L)
                files.setFile(fullFileId(50), FileState.Downloaded(path = "/files/50.jpg", size = 1_000))

                viewModel(messageId = 50)

                assertEquals(listOf("download:491:Prefetch", "download:511:Prefetch"), files.calls)
            }

        @Test
        fun `unfinished downloads are cancelled when the viewer closes`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                notDownloaded(1L..100L)
                files.setFile(fullFileId(51), FileState.Downloaded(path = "/files/51.jpg", size = 1_000))
                val viewModel = viewModel(messageId = 50)
                files.calls.clear()

                clear(viewModel)
                runCurrent()

                assertEquals(setOf("cancel:501", "cancel:491"), files.calls.toSet())
            }

        @Test
        fun `the shown item's and its neighbours' files are in the state`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                notDownloaded(1L..100L)
                val viewModel = viewModel(messageId = 50)

                files.setFile(fullFileId(50), FileState.Downloading(downloadedSize = 40, size = 100, isActive = true))
                runCurrent()

                val states = viewModel.content.files
                assertEquals(FileState.Downloading(40, 100, isActive = true), states[fullFileId(50)])
                assertEquals(
                    setOf(490, 491, 500, 501, 510, 511),
                    states.keys,
                )
            }

        @Test
        fun `a refused download of the current item is a snackbar`() =
            runViewModelTest {
                media.serve(photos(1L..100L))
                notDownloaded(1L..100L)
                files.onDownload = { Outcome.Failure(DomainError.Network) }

                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    assertEquals(MediaViewerEffect.ShowError(DomainError.Network), awaitItem())
                    expectNoEvents()
                }
            }
    }

    @Nested
    inner class Deletions {
        @Test
        fun `a deleted item leaves the pager`() =
            runViewModelTest {
                media.serve(photos(1L..5L))
                val viewModel = viewModel(messageId = 3)

                media.deletions.emit(setOf(2L, 99L))
                runCurrent()

                assertEquals(listOf(1L, 3L, 4L, 5L), viewModel.ids)
                assertEquals(3, viewModel.current)
            }

        @Test
        fun `a deleted current item gives way to the next newer one`() =
            runViewModelTest {
                media.serve(photos(1L..5L))
                val viewModel = viewModel(messageId = 3)

                media.deletions.emit(setOf(3L))
                runCurrent()

                assertEquals(4, viewModel.current)
            }

        @Test
        fun `with everything deleted the media is no longer available`() =
            runViewModelTest {
                media.serve(photos(1L..2L))
                val viewModel = viewModel(messageId = 1)

                media.deletions.emit(setOf(1L, 2L))
                runCurrent()

                assertEquals(MediaViewerUiState.Unavailable, viewModel.state)
            }
    }

    @Nested
    inner class Spoilers {
        @Test
        fun `the opened spoiler shows, others stay hidden until tapped`() =
            runViewModelTest {
                media.serve((1L..3L).map { message(it, photoContent(it, hasSpoiler = true)) })
                val viewModel = viewModel(messageId = 2)

                assertEquals(listOf(true, false, true), viewModel.content.items.map { it.isHidden })

                send(viewModel, MediaViewerEvent.RevealSpoiler(3))
                assertEquals(listOf(true, false, false), viewModel.content.items.map { it.isHidden })
            }

        @Test
        fun `self-destructing media is left out`() =
            runViewModelTest {
                media.serve(listOf(message(1), message(2, photoContent(2, isSecret = true)), message(3)))

                assertEquals(listOf(1L, 3L), viewModel(messageId = 1).ids)
            }

        @Test
        fun `captions come with their items`() =
            runViewModelTest {
                media.serve(listOf(message(1, photoContent(1, caption = "The engine"))))

                assertEquals(
                    "The engine",
                    viewModel(messageId = 1)
                        .content.current.caption.text,
                )
            }
    }

    @Nested
    inner class Playback {
        private val videoPath = "/files/video.mp4"

        /** A viewer on video 2 between photos 1 and 3, its file on the device, and the screen started. */
        private fun TestScope.playingVideo(): MediaViewerViewModel {
            media.serve(listOf(message(1), message(2, videoContent(2)), message(3)))
            files.setFile(fullFileId(2), FileState.Downloaded(videoPath, 12_000_000))
            return viewModel(messageId = 2).also { send(it, MediaViewerEvent.Started) }
        }

        @Test
        fun `a downloaded video plays once the screen is started`() =
            runViewModelTest {
                media.serve(listOf(message(1), message(2, videoContent(2)), message(3)))
                files.setFile(fullFileId(2), FileState.Downloaded(videoPath, 12_000_000))
                val viewModel = viewModel(messageId = 2)
                assertEquals(emptyList<String>(), player.calls)
                assertEquals(
                    VideoPlayback(
                        2,
                        isPlaying = false,
                        showsFrame = false,
                        progress = viewModel.content.playback!!.progress,
                        buffered = viewModel.content.playback!!.buffered,
                    ),
                    viewModel.content.playback,
                )

                send(viewModel, MediaViewerEvent.Started)

                assertEquals(listOf("play:$videoPath:Video"), player.calls)
                assertTrue(viewModel.content.playback!!.isPlaying)
            }

        @Test
        fun `a video downloading plays once it is on the device`() =
            runViewModelTest {
                media.serve(listOf(message(1), message(2, videoContent(2))))
                files.setFile(fullFileId(2), FileState.Downloading(1, 12_000_000, isActive = true))
                val viewModel = viewModel(messageId = 2)
                send(viewModel, MediaViewerEvent.Started)
                assertNull(viewModel.content.playback)

                files.setFile(fullFileId(2), FileState.Downloaded(videoPath, 12_000_000))
                runCurrent()

                assertEquals(listOf("play:$videoPath:Video"), player.calls)
            }

        @Test
        fun `a video that supports streaming plays as it downloads, which the viewer leaves to the stream`() =
            runViewModelTest {
                media.serve(listOf(message(1, videoContent(1, supportsStreaming = true))))
                files.setFile(fullFileId(1), FileState.NotDownloaded(12_000_000, canBeDownloaded = true))
                val viewModel = viewModel(messageId = 1)
                send(viewModel, MediaViewerEvent.Started)

                assertEquals(listOf("play:stream:${fullFileId(1)}:Video"), player.calls)
                assertTrue(files.calls.none { it.startsWith("download:${fullFileId(1)}:") })

                // The stream downloading it does not start the video again.
                files.setFile(fullFileId(1), FileState.Downloading(1_000_000, 12_000_000, isActive = true))
                files.setFile(fullFileId(1), FileState.Downloaded(videoPath, 12_000_000))
                runCurrent()
                assertEquals(listOf("play:stream:${fullFileId(1)}:Video"), player.calls)
            }

        @Test
        fun `a GIF plays as a GIF`() =
            runViewModelTest {
                media.serve(listOf(message(1, gifContent(1))), ChatMediaFilter.Gifs)
                files.setFile(fullFileId(1), FileState.Downloaded("/files/gif.mp4", 400_000))

                send(viewModel(messageId = 1), MediaViewerEvent.Started)

                assertEquals(listOf("play:/files/gif.mp4:Gif"), player.calls)
            }

        @Test
        fun `turning the page stops what plays`() =
            runViewModelTest {
                val viewModel = playingVideo()

                send(viewModel, MediaViewerEvent.PageSettled(3))

                assertEquals(listOf("play:$videoPath:Video", "stop"), player.calls)
                assertNull(viewModel.content.playback)
            }

        @Test
        fun `stopping the screen pauses, starting again leaves a video paused`() =
            runViewModelTest {
                val viewModel = playingVideo()

                send(viewModel, MediaViewerEvent.Stopped, MediaViewerEvent.Started)

                assertEquals(listOf("play:$videoPath:Video", "pause"), player.calls)
                assertFalse(viewModel.content.playback!!.isPlaying)
            }

        @Test
        fun `a GIF plays on when the screen starts again`() =
            runViewModelTest {
                media.serve(listOf(message(1, gifContent(1))), ChatMediaFilter.Gifs)
                files.setFile(fullFileId(1), FileState.Downloaded("/files/gif.mp4", 400_000))
                val viewModel = viewModel(messageId = 1)

                send(viewModel, MediaViewerEvent.Started, MediaViewerEvent.Stopped, MediaViewerEvent.Started)

                assertEquals(listOf("play:/files/gif.mp4:Gif", "pause", "resume"), player.calls)
            }

        @Test
        fun `play and pause toggle the video, and seek moves it`() =
            runViewModelTest {
                val viewModel = playingVideo()
                player.durationMillis = 60_000

                send(viewModel, MediaViewerEvent.PlayPause, MediaViewerEvent.Seek(0.5f), MediaViewerEvent.PlayPause)

                assertEquals(listOf("play:$videoPath:Video", "pause", "seek:30000", "resume"), player.calls)
                assertEquals(0.5f, viewModel.content.playback!!.progress())
            }

        @Test
        fun `the progress follows the player while it plays`() =
            runViewModelTest {
                val viewModel = playingVideo()
                player.durationMillis = 60_000
                player.positionMillis = 15_000

                advanceTimeBy(100)

                assertEquals(0.25f, viewModel.content.playback!!.progress())
            }

        @Test
        fun `the first frame uncovers the surface`() =
            runViewModelTest {
                val viewModel = playingVideo()

                player.renderFirstFrame()
                runCurrent()

                assertTrue(viewModel.content.playback!!.showsFrame)
            }

        @Test
        fun `after its end the video shows as paused and plays again from the start`() =
            runViewModelTest {
                val viewModel = playingVideo()
                player.end()
                runCurrent()
                assertFalse(viewModel.content.playback!!.isPlaying)

                send(viewModel, MediaViewerEvent.PlayPause)

                assertEquals("resume", player.calls.last())
                assertEquals(0f, viewModel.content.playback!!.progress())
            }

        @Test
        fun `the system pausing the player shows as paused`() =
            runViewModelTest {
                val viewModel = playingVideo()

                player.pauseBySystem()
                runCurrent()

                assertFalse(viewModel.content.playback!!.isPlaying)
            }

        @Test
        fun `a file that cannot be played is a snackbar`() =
            runViewModelTest {
                val viewModel = playingVideo()

                viewModel.effects.test {
                    player.fail()
                    assertEquals(MediaViewerEffect.PlaybackFailed, awaitItem())
                }
                runCurrent()
                assertNull(viewModel.content.playback)
            }

        @Test
        fun `a file that cannot be played is not tried again until the page comes back`() =
            runViewModelTest {
                val viewModel = playingVideo()
                player.fail()
                runCurrent()

                // Something else changes: more media loads.
                media.deletions.emit(setOf(1L))
                runCurrent()
                assertEquals(1, player.calls.count { it.startsWith("play") })

                send(viewModel, MediaViewerEvent.PageSettled(3), MediaViewerEvent.PageSettled(2))
                assertEquals(2, player.calls.count { it.startsWith("play") })
            }

        @Test
        fun `the player is released with the view model`() =
            runViewModelTest {
                val viewModel = playingVideo()

                clear(viewModel)

                assertEquals("release", player.calls.last())
            }

        @Test
        fun `photos play nothing`() =
            runViewModelTest {
                media.serve(photos(1L..3L))
                files.setFile(fullFileId(2), FileState.Downloaded("/files/2.jpg", 1_000))

                send(viewModel(messageId = 2), MediaViewerEvent.Started)

                assertEquals(emptyList<String>(), player.calls)
            }
    }

    private companion object {
        val SOMEONE = dev.kyralabs.pingogram.core.ui.R.string.preview_someone
    }
}
