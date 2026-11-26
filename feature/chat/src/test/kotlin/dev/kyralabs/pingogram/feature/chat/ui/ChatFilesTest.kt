package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The files' states of [ChatFiles] as the loaded messages change. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatFilesTest {
    private val files = FakeFileRepository()
    private val document = ContentPreviewData.document("report.pdf", "application/pdf")
    private val documentFile = document.document.file.id
    private val voice = ContentPreviewData.voiceNote(isListened = false)
    private val downloaded = FileState.Downloaded(path = "/files/report.pdf", size = 1_000)

    @Test
    fun `a file still loaded keeps its state while other messages load or go`() =
        runTest {
            files.setFile(documentFile, downloaded)
            val history = MutableStateFlow(history(message(1, content = document)))
            val states = mutableListOf<Map<Int, FileState>>()
            backgroundScope.launch { ChatFiles(files, backgroundScope) {}.statesOf(history).toList(states) }
            runCurrent()

            history.value = history(message(1, content = document), message(2, content = voice))
            runCurrent()
            history.value = history(message(1, content = document))
            runCurrent()

            // Before TDLib first reported it, nothing; never again once it did.
            assertEquals(listOf(emptyMap(), mapOf(documentFile to downloaded)), states)
        }
}
