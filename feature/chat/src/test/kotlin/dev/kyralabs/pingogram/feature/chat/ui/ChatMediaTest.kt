package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Media in [ChatViewModel]: the files it follows, what tapping media does, and albums as one bubble. Playback is in
 * [ChatPlaybackTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMediaTest : ChatViewModelHarness() {
    private val voice = ContentPreviewData.voiceNote(isListened = true)
    private val voiceFile = voice.voiceNote.file.id
    private val document = ContentPreviewData.document("report.pdf", "application/pdf")
    private val documentFile = document.document.file.id

    // Files

    @Test
    fun `follows the files of loaded voice messages, songs and documents`() =
        runTest {
            files.setFile(voiceFile, FileState.Downloaded(path = "/voice.ogg", size = 1))
            files.setFile(documentFile, FileState.Downloading(downloadedSize = 1, size = 4, isActive = true))

            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, content = voice),
                        message(2, content = document),
                        message(3, content = ContentPreviewData.photo(minithumbnail = null)),
                    ),
                )

            assertEquals(
                persistentMapOf(
                    voiceFile to FileState.Downloaded(path = "/voice.ogg", size = 1),
                    documentFile to FileState.Downloading(downloadedSize = 1, size = 4, isActive = true),
                ),
                viewModel.state.fileStates,
            )
            assertEquals(setOf(voiceFile, documentFile), files.observed.toSet())
        }

    @Test
    fun `a file TDLib has not reported shows once it does`() =
        runTest {
            val viewModel = showing(chat(), history(message(1, content = document), message(2)))
            assertTrue(viewModel.state.fileStates.isEmpty())

            files.setFile(documentFile, FileState.NotDownloaded(size = 4, canBeDownloaded = true))
            runCurrent()

            assertEquals(
                FileState.NotDownloaded(size = 4, canBeDownloaded = true),
                viewModel.state.fileStates[documentFile],
            )
        }

    @Test
    fun `a file whose message is no longer loaded is dropped`() =
        runTest {
            files.setFile(documentFile, FileState.NotDownloaded(size = 4, canBeDownloaded = true))
            val viewModel = showing(chat(), history(message(1, content = document)))

            messages.setHistory(PRIVATE_CHAT, history(message(2)))
            runCurrent()

            assertNull(viewModel.state.fileStates[documentFile])
        }

    @Test
    fun `download and cancel reach the repository`() =
        runTest {
            val viewModel = showing(chat(), history(message(1, content = document)))

            send(viewModel, ChatEvent.DownloadFile(documentFile), ChatEvent.CancelDownload(documentFile))

            assertEquals(listOf("download:$documentFile:Visible", "cancel:$documentFile"), files.calls)
        }

    @Test
    fun `a failed download or cancel shows its error`() =
        runTest {
            files.onDownload = { Outcome.Failure(DomainError.Network) }
            files.onCancel = { Outcome.Failure(DomainError.NotFound) }
            val viewModel = showing(chat(), history(message(1, content = document)))

            viewModel.effects.test {
                send(viewModel, ChatEvent.DownloadFile(documentFile))
                assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                send(viewModel, ChatEvent.CancelDownload(documentFile))
                assertEquals(ChatEffect.ShowError(DomainError.NotFound), awaitItem())
            }
        }

    @Test
    fun `opening a downloaded file says that it comes later`() =
        runTest {
            val viewModel = showing(chat(), history(message(1, content = document)))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenFile(messageId = 1, fileId = documentFile))
                assertEquals(ChatEffect.FileOpeningUnavailable, awaitItem())
            }
        }

    @Test
    fun `photos, videos and GIFs open in the media viewer`() =
        runTest {
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, content = ContentPreviewData.photo(minithumbnail = null)),
                        message(2, content = ContentPreviewData.video(minithumbnail = null)),
                        message(3, content = ContentPreviewData.animation(minithumbnail = null)),
                    ),
                )

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenMedia(1), ChatEvent.OpenMedia(2), ChatEvent.OpenMedia(3))
                assertEquals(ChatEffect.OpenMedia(1), awaitItem())
                assertEquals(ChatEffect.OpenMedia(2), awaitItem())
                assertEquals(ChatEffect.OpenMedia(3), awaitItem())
            }
            assertTrue(files.calls.isEmpty())
        }

    @Test
    fun `video messages and self-destructing media do not open in the media viewer`() =
        runTest {
            val secret = ContentPreviewData.photo(minithumbnail = null).copy(isSecret = true)
            val videoNote = ContentPreviewData.videoNote(minithumbnail = null, isViewed = false)
            val viewModel = showing(chat(), history(message(1, content = secret), message(2, content = videoNote)))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenMedia(1), ChatEvent.OpenMedia(2), ChatEvent.OpenMedia(99))
                expectNoEvents()
            }
        }

    // Locations

    @Test
    fun `a location opens in a maps app`() =
        runTest {
            val location = MessageContent.Location(Location(52.5, 13.4), livePeriod = 0)
            val viewModel = showing(chat(), history(message(1, content = location)))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenLocation(messageId = 1))
                assertEquals(ChatEffect.OpenLocation(52.5, 13.4, label = null), awaitItem())
            }
        }

    @Test
    fun `a venue opens in a maps app with its name`() =
        runTest {
            val venue = ContentPreviewData.venue
            val viewModel = showing(chat(), history(message(1, content = venue)))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenLocation(messageId = 1))
                val effect = awaitItem() as ChatEffect.OpenLocation
                assertEquals(venue.venue.location.latitude, effect.latitude)
                assertEquals(venue.venue.location.longitude, effect.longitude)
                assertEquals("Brandenburger Tor", effect.label)
            }
        }

    @Test
    fun `the location link has dot decimals and an encoded name`() {
        assertEquals(
            "geo:52.516275,13.377704?q=52.516275,13.377704(Brandenburger%20Tor)",
            geoUri(ChatEffect.OpenLocation(52.516275, 13.377704, "Brandenburger Tor")),
        )
        assertEquals(
            "geo:-33.856800,151.215300?q=-33.856800,151.215300",
            geoUri(ChatEffect.OpenLocation(-33.8568, 151.2153, label = null)),
        )
    }

    // Albums

    private fun albumPhoto(
        id: Long,
        caption: String = "",
        album: Long = ALBUM,
        reactions: InteractionInfo? = null,
    ) = message(
        id,
        date = NOW.minus(Duration.ofSeconds(ALBUM_SIZE - id)),
        content = ContentPreviewData.photo(minithumbnail = null).copy(caption = text(caption)),
        mediaAlbumId = album,
        interactionInfo = reactions,
    )

    @Test
    fun `the photos and videos of an album are one bubble`() =
        runTest {
            val reactions =
                InteractionInfo(0, 0, null, persistentListOf(Reaction(ReactionType.Emoji("🎉"), 2, isChosen = false)))
            val viewModel =
                showing(
                    chat(),
                    history(
                        albumPhoto(1),
                        albumPhoto(2, caption = "Our trip"),
                        albumPhoto(3, reactions = reactions),
                        message(4),
                    ),
                )

            assertEquals(listOf("m4", "m2", "date:2026-10-18"), viewModel.outline())
            val bubble = viewModel.bubble(2)
            assertEquals(listOf(1L, 2L, 3L), bubble.album?.messageIds)
            assertEquals(3, bubble.album?.items?.size)
            assertEquals(albumKey(ALBUM), bubble.key)
            assertEquals("Our trip", bubble.text.text)
            assertEquals(1, bubble.reactions.size)
            assertEquals(plural(R.plurals.content_album_photos, 3), bubble.contentLabel)
        }

    @Test
    fun `an album without a caption is its newest message`() =
        runTest {
            val viewModel = showing(chat(), history(albumPhoto(1), albumPhoto(2)))

            assertEquals(listOf(2L), viewModel.bubbles.map { it.id })
            assertEquals(FormattedText.Empty.text, viewModel.bubble(2).text.text)
        }

    @Test
    fun `each item of an album opens in the media viewer by its own message`() =
        runTest {
            val viewModel = showing(chat(), history(albumPhoto(1), albumPhoto(2, caption = "Our trip"), albumPhoto(3)))

            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenMedia(1), ChatEvent.OpenMedia(3))
                assertEquals(ChatEffect.OpenMedia(1), awaitItem())
                assertEquals(ChatEffect.OpenMedia(3), awaitItem())
            }
        }

    @Test
    fun `an album split across pages becomes one bubble as the older page loads`() =
        runTest {
            val viewModel =
                showing(chat(), history(albumPhoto(3), albumPhoto(4), message(5), reachedStart = false))
            val before = viewModel.bubbles.single { it.album != null }
            assertEquals(listOf(3L, 4L), before.album?.messageIds)

            messages.setHistory(
                PRIVATE_CHAT,
                history(albumPhoto(1), albumPhoto(2), albumPhoto(3), albumPhoto(4), message(5)),
            )
            runCurrent()

            val after = viewModel.bubbles.single { it.album != null }
            assertEquals(listOf(1L, 2L, 3L, 4L), after.album?.messageIds)
            assertEquals(before.key, after.key)
            assertEquals(2, viewModel.bubbles.size)
        }

    @Test
    fun `consecutive albums stay apart, and documents of an album keep their own bubbles`() =
        runTest {
            val documents = (5L..6L).map { message(it, content = document, mediaAlbumId = OTHER_ALBUM + 1) }
            val viewModel =
                showing(
                    chat(),
                    history(
                        albumPhoto(1),
                        albumPhoto(2),
                        albumPhoto(3, album = OTHER_ALBUM),
                        albumPhoto(4, album = OTHER_ALBUM),
                        *documents.toTypedArray(),
                    ),
                )

            assertEquals(listOf("m6", "m5", "m4", "m2", "date:2026-10-18"), viewModel.outline())
            assertEquals(listOf(null, null), viewModel.bubbles.take(2).map { it.album })
        }

    private companion object {
        const val ALBUM = 77L
        const val OTHER_ALBUM = 88L
        const val ALBUM_SIZE = 10L
    }
}
