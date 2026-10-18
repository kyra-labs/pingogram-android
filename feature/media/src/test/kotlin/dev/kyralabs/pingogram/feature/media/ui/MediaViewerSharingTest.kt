package dev.kyralabs.pingogram.feature.media.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.CHAT
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.chat
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.fullFileId
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.message
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.photos
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.videoContent
import dev.kyralabs.pingogram.feature.media.ui.files.GalleryItem
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
 * Share and Save in [MediaViewerViewModel] (`MediaSharing`): offered only where Telegram lets the content leave it,
 * waiting for the file to download, the share effect, the copy into the gallery and its snackbars, and the storage
 * permission of Android 8–9.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerSharingTest : MediaViewerViewModelHarness() {
    private val path = "/data/files/tdlib/files/photos/file_50.jpg"

    @BeforeEach
    fun openChat() {
        chats.chats.value = mapOf(CHAT to chat(CHAT, "Engines"))
        media.serve(photos(1L..100L))
    }

    private fun downloaded(messageId: Long = 50) {
        files.setFile(fullFileId(messageId), FileState.Downloaded(path, size = 1_000))
    }

    private fun downloading(messageId: Long = 50) {
        files.setFile(fullFileId(messageId), FileState.Downloading(downloadedSize = 400, size = 1_000, isActive = true))
    }

    private val MediaViewerViewModel.actions get() = content.actions

    @Nested
    inner class Offered {
        @Test
        fun `Share and Save show where the chat and TDLib let the content leave Telegram`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                assertTrue(viewModel.actions.canSave)
                assertEquals(listOf(50L), media.saveChecks)
            }

        @Test
        fun `a chat that protects its content hides them`() =
            runViewModelTest {
                chats.chats.value = mapOf(CHAT to chat(CHAT, "Engines").copy(hasProtectedContent = true))
                downloaded()
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Share, MediaViewerEvent.Save)

                    assertFalse(viewModel.actions.canSave)
                    expectNoEvents()
                }
                assertTrue(fileActions.saved.isEmpty())
            }

        @Test
        fun `a chat that starts protecting its content hides them at once`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                chats.chats.value = mapOf(CHAT to chat(CHAT, "Engines").copy(hasProtectedContent = true))
                runCurrent()

                assertFalse(viewModel.actions.canSave)
            }

        @Test
        fun `a message TDLib does not let save hides them`() =
            runViewModelTest {
                media.onCanBeSaved = { Outcome.Success(false) }

                val viewModel = viewModel(messageId = 50)

                assertFalse(viewModel.actions.canSave)
            }

        @Test
        fun `they do not show until TDLib answers, nor when it fails`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<Boolean>>()
                media.onCanBeSaved = { answer.await() }
                val viewModel = viewModel(messageId = 50)
                assertFalse(viewModel.actions.canSave)

                answer.complete(Outcome.Failure(DomainError.Network))
                runCurrent()

                assertFalse(viewModel.actions.canSave)
            }

        @Test
        fun `TDLib is asked once per item while the viewer lives`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                send(
                    viewModel,
                    MediaViewerEvent.PageSettled(51),
                    MediaViewerEvent.PageSettled(50),
                    MediaViewerEvent.PageSettled(51),
                )

                assertEquals(listOf(50L, 51L), media.saveChecks)
            }

        @Test
        fun `in a chat TDLib has not announced they do not show`() =
            runViewModelTest {
                chats.chats.value = emptyMap()

                val viewModel = viewModel(messageId = 50)

                assertFalse(viewModel.actions.canSave)
            }
    }

    @Nested
    inner class Share {
        @Test
        fun `a downloaded file goes to the share sheet at once`() =
            runViewModelTest {
                downloaded()
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Share)

                    assertEquals(MediaViewerEffect.ShareFile(path, "image/jpeg"), awaitItem())
                }
                assertNull(viewModel.actions.pending)
            }

        @Test
        fun `a file still downloading is shared once it is there, with the wait shown meanwhile`() =
            runViewModelTest {
                downloading()
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Share)
                    assertEquals(MediaAction.Share, viewModel.actions.pending)
                    expectNoEvents()

                    downloaded()
                    runCurrent()

                    assertEquals(MediaViewerEffect.ShareFile(path, "image/jpeg"), awaitItem())
                }
                assertNull(viewModel.actions.pending)
                assertTrue(files.calls.contains("download:${fullFileId(50)}:Visible"))
            }

        @Test
        fun `a video is shared as a video`() =
            runViewModelTest {
                media.serve(listOf(message(50, videoContent(50))))
                files.setFile(fullFileId(50), FileState.Downloaded("/data/files/tdlib/files/videos/file_50", 1_000))
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Share)

                    assertEquals(
                        MediaViewerEffect.ShareFile("/data/files/tdlib/files/videos/file_50", "video/mp4"),
                        awaitItem(),
                    )
                }
            }

        @Test
        fun `turning the page drops the wait`() =
            runViewModelTest {
                downloading()
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Share, MediaViewerEvent.PageSettled(51))
                    downloaded()
                    runCurrent()

                    assertNull(viewModel.actions.pending)
                    expectNoEvents()
                }
            }

        @Test
        fun `a file TDLib cannot download cannot be shared`() =
            runViewModelTest {
                files.setFile(fullFileId(50), FileState.NotDownloaded(size = 1_000, canBeDownloaded = false))
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Share)

                    assertEquals(MediaViewerEffect.ShareFailed, awaitItem())
                }
                assertNull(viewModel.actions.pending)
            }

        @Test
        fun `a failed download ends the wait, and shows its error`() =
            runViewModelTest {
                files.setFile(fullFileId(50), FileState.NotDownloaded(size = 1_000, canBeDownloaded = true))
                val failure = CompletableDeferred<Outcome<Unit>>()
                files.onDownload = { failure.await() }
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Share)
                    assertEquals(MediaAction.Share, viewModel.actions.pending)

                    failure.complete(Outcome.Failure(DomainError.Network))
                    runCurrent()

                    assertEquals(MediaViewerEffect.ShowError(DomainError.Network), awaitItem())
                }
                assertNull(viewModel.actions.pending)
            }
    }

    @Nested
    inner class Save {
        @Test
        fun `a downloaded photo is copied into the gallery, named after its message, and a snackbar says so`() =
            runViewModelTest {
                downloaded()
                val copy = CompletableDeferred<Boolean>()
                fileActions.onSave = { copy.await() }
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Save)
                    assertEquals(MediaAction.Save, viewModel.actions.pending)

                    copy.complete(true)
                    runCurrent()

                    assertEquals(MediaViewerEffect.SavedToGallery, awaitItem())
                }
                assertEquals(listOf(GalleryItem(path, "image/jpeg", "Pingogram_50")), fileActions.saved)
                assertNull(viewModel.actions.pending)
            }

        @Test
        fun `a failed copy is a snackbar`() =
            runViewModelTest {
                downloaded()
                fileActions.onSave = { false }
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Save)

                    assertEquals(MediaViewerEffect.SaveFailed, awaitItem())
                }
                assertNull(viewModel.actions.pending)
            }

        @Test
        fun `a file still downloading is saved once it is there`() =
            runViewModelTest {
                downloading()
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Save)
                    assertTrue(fileActions.saved.isEmpty())

                    downloaded()
                    runCurrent()

                    assertEquals(MediaViewerEffect.SavedToGallery, awaitItem())
                }
                assertEquals(1, fileActions.saved.size)
            }

        @Test
        fun `a second tap while one waits does nothing`() =
            runViewModelTest {
                downloading()
                val viewModel = viewModel(messageId = 50)

                send(viewModel, MediaViewerEvent.Save, MediaViewerEvent.Share)
                downloaded()
                runCurrent()

                assertEquals(1, fileActions.saved.size)
            }
    }

    @Nested
    inner class StoragePermission {
        @BeforeEach
        fun androidNine() {
            fileActions.needsPermission = true
            downloaded()
        }

        @Test
        fun `on Android 8 and 9 saving checks the permission first`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Save)

                    assertEquals(MediaViewerEffect.CheckStoragePermission, awaitItem())
                }
                assertTrue(fileActions.saved.isEmpty())
            }

        @Test
        fun `without a rationale, the permission is asked for at once`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Save)
                    assertEquals(MediaViewerEffect.CheckStoragePermission, awaitItem())
                    send(viewModel, MediaViewerEvent.StoragePermissionChecked(showRationale = false))

                    assertEquals(MediaViewerEffect.RequestStoragePermission, awaitItem())
                }
                assertFalse(viewModel.actions.showsStorageRationale)
            }

        @Test
        fun `with a rationale, it shows first, and Continue asks for the permission`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Save)
                    assertEquals(MediaViewerEffect.CheckStoragePermission, awaitItem())
                    send(viewModel, MediaViewerEvent.StoragePermissionChecked(showRationale = true))
                    assertTrue(viewModel.actions.showsStorageRationale)
                    expectNoEvents()

                    send(viewModel, MediaViewerEvent.StorageRationaleAnswered(proceed = true))

                    assertEquals(MediaViewerEffect.RequestStoragePermission, awaitItem())
                }
                assertFalse(viewModel.actions.showsStorageRationale)
            }

        @Test
        fun `Not now leaves it`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Save)
                    assertEquals(MediaViewerEffect.CheckStoragePermission, awaitItem())
                    send(
                        viewModel,
                        MediaViewerEvent.StoragePermissionChecked(showRationale = true),
                        MediaViewerEvent.StorageRationaleAnswered(proceed = false),
                        MediaViewerEvent.StoragePermissionResult(granted = true),
                    )

                    assertFalse(viewModel.actions.showsStorageRationale)
                    expectNoEvents()
                }
                assertTrue(fileActions.saved.isEmpty())
            }

        @Test
        fun `a granted permission saves the photo`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Save)
                    assertEquals(MediaViewerEffect.CheckStoragePermission, awaitItem())
                    send(
                        viewModel,
                        MediaViewerEvent.StoragePermissionChecked(showRationale = false),
                        MediaViewerEvent.StoragePermissionResult(granted = true),
                    )
                    assertEquals(MediaViewerEffect.RequestStoragePermission, awaitItem())

                    assertEquals(MediaViewerEffect.SavedToGallery, awaitItem())
                }
                assertEquals(listOf(GalleryItem(path, "image/jpeg", "Pingogram_50")), fileActions.saved)
            }

        @Test
        fun `a refused permission is a snackbar, and nothing is saved`() =
            runViewModelTest {
                val viewModel = viewModel(messageId = 50)

                viewModel.effects.test {
                    send(viewModel, MediaViewerEvent.Save)
                    assertEquals(MediaViewerEffect.CheckStoragePermission, awaitItem())
                    send(
                        viewModel,
                        MediaViewerEvent.StoragePermissionChecked(showRationale = false),
                        MediaViewerEvent.StoragePermissionResult(granted = false),
                    )
                    assertEquals(MediaViewerEffect.RequestStoragePermission, awaitItem())

                    assertEquals(MediaViewerEffect.StoragePermissionDenied, awaitItem())
                }
                assertTrue(fileActions.saved.isEmpty())
            }

        @Test
        fun `a permission granted after process death still saves the photo it was asked for`() =
            runViewModelTest {
                val handle = SavedStateHandle()
                val first = viewModel(messageId = 50, handle = handle)
                send(first, MediaViewerEvent.Save)
                clear(first)

                val restored = viewModel(messageId = 50, handle = handle)
                restored.effects.test {
                    send(restored, MediaViewerEvent.StoragePermissionResult(granted = true))

                    assertEquals(MediaViewerEffect.SavedToGallery, awaitItem())
                }
                assertEquals(listOf(GalleryItem(path, "image/jpeg", "Pingogram_50")), fileActions.saved)
            }
    }
}
