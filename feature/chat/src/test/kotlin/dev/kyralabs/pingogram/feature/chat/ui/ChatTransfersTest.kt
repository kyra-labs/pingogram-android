package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * How the bubbles read the conversation's transfers ([ChatTransfers], `docs/PERFORMANCE.md`): a tick of one file or
 * upload recomposes the bubble it belongs to, and no other.
 */
@RunWith(RobolectricTestRunner::class)
class ChatTransfersTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val fileStates = MutableStateFlow<ImmutableMap<Int, FileState>>(persistentMapOf())
    private val uploads = MutableStateFlow<ImmutableMap<Long, MessageUpload>>(persistentMapOf())
    private val compositions = mutableMapOf<Long, Int>()
    private val shown = mutableMapOf<Long, Any?>()

    @Test
    fun `a download's progress recomposes its own bubble alone`() {
        fileStates.value = persistentMapOf(FIRST_FILE to downloading(1), SECOND_FILE to downloading(1))
        setRows { transfers ->
            FileRow(FIRST, FIRST_FILE, transfers)
            FileRow(SECOND, SECOND_FILE, transfers)
        }

        fileStates.value = persistentMapOf(FIRST_FILE to downloading(2), SECOND_FILE to downloading(1))
        composeRule.waitForIdle()

        assertEquals(downloading(2), shown[FIRST])
        assertEquals(mapOf(FIRST to 2, SECOND to 1), compositions)
    }

    @Test
    fun `an upload's progress recomposes its own bubble alone`() {
        uploads.value = persistentMapOf(FIRST to MessageUpload(0.1f), SECOND to MessageUpload(0.1f))
        setRows { transfers ->
            UploadRow(FIRST, transfers)
            UploadRow(SECOND, transfers)
        }

        uploads.value = persistentMapOf(FIRST to MessageUpload(0.5f), SECOND to MessageUpload(0.1f))
        composeRule.waitForIdle()

        assertEquals(mapOf(FIRST to MessageUpload(0.5f)), shown[FIRST])
        assertEquals(mapOf(FIRST to 2, SECOND to 1), compositions)
    }

    private fun setRows(rows: @Composable (TransferStates) -> Unit) {
        val transfers = ChatTransfers(fileStates, uploads)
        composeRule.setContent { rows(transfers.collect()) }
        composeRule.waitForIdle()
    }

    @Composable
    private fun FileRow(
        id: Long,
        fileId: Int,
        transfers: TransferStates,
    ) {
        val state = transfers.fileState(fileId)
        SideEffect {
            shown[id] = state
            compositions[id] = (compositions[id] ?: 0) + 1
        }
    }

    @Composable
    private fun UploadRow(
        id: Long,
        transfers: TransferStates,
    ) {
        val bubble = ChatPreviewData.bubble(id = id, text = "", time = "9:41 AM", isOutgoing = true)
        val upload = transfers.uploadsOf(bubble)
        SideEffect {
            shown[id] = upload
            compositions[id] = (compositions[id] ?: 0) + 1
        }
    }

    private fun downloading(downloaded: Long) = FileState.Downloading(downloaded, size = 4, isActive = true)

    private companion object {
        const val FIRST = 1L
        const val SECOND = 2L
        const val FIRST_FILE = 10
        const val SECOND_FILE = 20
    }
}
