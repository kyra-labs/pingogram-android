package dev.kyralabs.pingogram.feature.media.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.feature.media.ui.FakeMessageActionRepository.Companion.properties
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.CHAT
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.chat
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.fullFileId
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.message
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.photos
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.videoContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The toolbar's other actions in [MediaViewerViewModel]: Delete (`MediaDeletion`) where TDLib allows it, with its
 * confirmation, Forward where TDLib allows it, "Show in chat", and Copy of a photo (`MediaSharing`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerActionsTest : MediaViewerViewModelHarness() {
    @BeforeEach
    fun openChat() {
        chats.chats.value = mapOf(CHAT to chat(CHAT, "Engines"))
        media.serve(photos(1L..100L))
    }

    private val MediaViewerViewModel.actions get() = content.actions

    @Nested
    inner class Delete {
        @Test
        fun `shows where TDLib lets the user delete the message, asked once per item`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                send(viewModel, MediaViewerEvent.PageSettled(51), MediaViewerEvent.PageSettled(50))

                assertTrue(viewModel.actions.canDelete)
                assertEquals(listOf(50L, 51L), messageActions.propertyChecks)
            }

        @Test
        fun `does not show where TDLib allows no deletion, nor until it answers or when it fails`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<MessageProperties>>()
                messageActions.onProperties = { id ->
                    if (id == 50L) answer.await() else Outcome.Success(properties(forSelf = false, forEveryone = false))
                }
                val viewModel = viewModel(messageId = 50)
                assertFalse(viewModel.actions.canDelete)

                answer.complete(Outcome.Failure(DomainError.Network))
                runCurrent()
                assertFalse(viewModel.actions.canDelete)

                send(viewModel, MediaViewerEvent.PageSettled(51))
                assertFalse(viewModel.actions.canDelete)
            }

        @Test
        fun `asks first, with for whom TDLib allows it`() =
            runViewModelTest {
                messageActions.onProperties = { id ->
                    Outcome.Success(
                        when (id) {
                            50L -> properties(forSelf = true, forEveryone = true)
                            51L -> properties(forSelf = false, forEveryone = true)
                            else -> properties(forSelf = true, forEveryone = false)
                        },
                    )
                }
                val viewModel = viewModel(messageId = 50)

                send(viewModel, MediaViewerEvent.Delete)
                assertEquals(DeleteConfirmation(50, DeleteScope.Choice), viewModel.actions.deleteConfirmation)
                send(
                    viewModel,
                    MediaViewerEvent.DeleteDismissed,
                    MediaViewerEvent.PageSettled(51),
                    MediaViewerEvent.Delete,
                )
                assertEquals(DeleteConfirmation(51, DeleteScope.ForEveryone), viewModel.actions.deleteConfirmation)
                send(
                    viewModel,
                    MediaViewerEvent.DeleteDismissed,
                    MediaViewerEvent.PageSettled(49),
                    MediaViewerEvent.Delete,
                )
                assertEquals(DeleteConfirmation(49, DeleteScope.ForMe), viewModel.actions.deleteConfirmation)
                assertTrue(messageActions.deleted.isEmpty())
            }

        @Test
        fun `cancelling deletes nothing`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                send(viewModel, MediaViewerEvent.Delete, MediaViewerEvent.DeleteDismissed)

                assertNull(viewModel.actions.deleteConfirmation)
                assertTrue(messageActions.deleted.isEmpty())
            }

        @Test
        fun `turning the page takes the confirmation away`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                send(viewModel, MediaViewerEvent.Delete, MediaViewerEvent.PageSettled(51))

                assertNull(viewModel.actions.deleteConfirmation)
            }

        @Test
        fun `confirming deletes the message for whom the user chose`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                send(viewModel, MediaViewerEvent.Delete, MediaViewerEvent.DeleteConfirmed(forEveryone = false))
                send(viewModel, MediaViewerEvent.Delete, MediaViewerEvent.DeleteConfirmed(forEveryone = true))

                assertEquals(listOf("50:false", "50:true"), messageActions.deleted)
                assertNull(viewModel.actions.deleteConfirmation)
            }

        @Test
        fun `where only one way is allowed, that one is used`() =
            runViewModelTest {
                messageActions.onProperties = { Outcome.Success(properties(forSelf = false, forEveryone = true)) }
                val viewModel = viewModel(messageId = 50)

                send(viewModel, MediaViewerEvent.Delete, MediaViewerEvent.DeleteConfirmed(forEveryone = false))

                assertEquals(listOf("50:true"), messageActions.deleted)
            }

        @Test
        fun `the deleted item gives way to the next one`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Delete, MediaViewerEvent.DeleteConfirmed(forEveryone = false))
                    media.deletions.emit(setOf(50L))
                    runCurrent()

                    assertEquals(51L, viewModel.current)
                    expectNoEvents()
                }
            }

        @Test
        fun `deleting the last item closes the viewer`() =
            runViewModelTest {
                media.serve(photos(50L..50L))
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Delete, MediaViewerEvent.DeleteConfirmed(forEveryone = false))

                    assertEquals(MediaViewerEffect.Close, awaitItem())
                }
            }

        @Test
        fun `a failed deletion shows its error`() =
            runViewModelTest {
                messageActions.onDelete = { Outcome.Failure(DomainError.Network) }
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Delete, MediaViewerEvent.DeleteConfirmed(forEveryone = false))

                    assertEquals(MediaViewerEffect.ShowError(DomainError.Network), awaitItem())
                }
                assertEquals(50L, viewModel.current)
            }
    }

    @Nested
    inner class Forward {
        @Test
        fun `shows where TDLib lets the message be forwarded, and opens the sheet for the current item`() =
            runViewModelTest {
                messageActions.onProperties = { Outcome.Success(properties(forward = true)) }
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.PageSettled(51))
                    assertTrue(viewModel.actions.canForward)
                    send(viewModel, MediaViewerEvent.Forward)

                    assertEquals(MediaViewerEffect.Forward(CHAT, 51), awaitItem())
                }
            }

        @Test
        fun `protected content does not show it, and the event opens nothing`() =
            runViewModelTest {
                chats.chats.value = mapOf(CHAT to chat(CHAT, "Engines").copy(hasProtectedContent = true))
                messageActions.onProperties = {
                    Outcome.Success(properties(forward = false).copy(canBeSaved = false))
                }
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Forward)

                    expectNoEvents()
                }
                assertFalse(viewModel.actions.canForward)
                assertFalse(viewModel.actions.canSave)
            }

        @Test
        fun `does not show for a message TDLib does not let be forwarded, nor until it answers or when it fails`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<MessageProperties>>()
                messageActions.onProperties = { id ->
                    if (id == 50L) answer.await() else Outcome.Success(properties(forward = false))
                }
                val viewModel = viewModel(messageId = 50)
                assertFalse(viewModel.actions.canForward)

                answer.complete(Outcome.Failure(DomainError.Network))
                runCurrent()
                assertFalse(viewModel.actions.canForward)

                send(viewModel, MediaViewerEvent.PageSettled(51))
                assertFalse(viewModel.actions.canForward)
            }
    }

    @Nested
    inner class ShowInChat {
        @Test
        fun `opens the conversation at the current item's message`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.PageSettled(51), MediaViewerEvent.ShowInChat)

                    assertEquals(MediaViewerEffect.ShowInChat(CHAT, 51), awaitItem())
                }
            }
    }

    @Nested
    inner class Copy {
        private val path = "/data/files/tdlib/files/photos/file_50.jpg"

        @Test
        fun `a downloaded photo goes to the clipboard`() =
            runViewModelTest {
                files.setFile(fullFileId(50), FileState.Downloaded(path, size = 1_000))
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Copy)

                    assertEquals(MediaViewerEffect.CopyImage(path, "image/jpeg"), awaitItem())
                }
            }

        @Test
        fun `a photo still downloading is copied once it is there, with the wait shown meanwhile`() =
            runViewModelTest {
                files.setFile(
                    fullFileId(50),
                    FileState.Downloading(downloadedSize = 400, size = 1_000, isActive = true),
                )
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Copy)
                    assertEquals(MediaAction.Copy, viewModel.actions.pending)

                    files.setFile(fullFileId(50), FileState.Downloaded(path, size = 1_000))
                    runCurrent()

                    assertEquals(MediaViewerEffect.CopyImage(path, "image/jpeg"), awaitItem())
                }
                assertNull(viewModel.actions.pending)
            }

        @Test
        fun `a video is not copied`() =
            runViewModelTest {
                media.serve(listOf(message(50, videoContent(50))))
                files.setFile(fullFileId(50), FileState.Downloaded("/data/files/tdlib/files/videos/file_50", 1_000))
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Copy)

                    expectNoEvents()
                }
                assertNull(viewModel.actions.pending)
            }

        @Test
        fun `a chat that protects its content does not let it be copied`() =
            runViewModelTest {
                chats.chats.value = mapOf(CHAT to chat(CHAT, "Engines").copy(hasProtectedContent = true))
                files.setFile(fullFileId(50), FileState.Downloaded(path, size = 1_000))
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Copy)

                    expectNoEvents()
                }
            }

        @Test
        fun `a photo TDLib cannot download cannot be copied`() =
            runViewModelTest {
                files.setFile(fullFileId(50), FileState.NotDownloaded(size = 1_000, canBeDownloaded = false))
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Copy)

                    assertEquals(MediaViewerEffect.CopyFailed, awaitItem())
                }
            }
    }
}
