package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatAction
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChatActionMappersTest {
    @Test
    fun `maps every action, with the progress of uploads and the emoji of animations`() {
        val actions =
            listOf(
                TdApi.ChatActionTyping(),
                TdApi.ChatActionRecordingVoiceNote(),
                TdApi.ChatActionUploadingVoiceNote(10),
                TdApi.ChatActionRecordingVideo(),
                TdApi.ChatActionUploadingVideo(20),
                TdApi.ChatActionRecordingVideoNote(),
                TdApi.ChatActionUploadingVideoNote(30),
                TdApi.ChatActionUploadingPhoto(40),
                TdApi.ChatActionUploadingDocument(50),
                TdApi.ChatActionChoosingSticker(),
                TdApi.ChatActionChoosingLocation(),
                TdApi.ChatActionChoosingContact(),
                TdApi.ChatActionStartPlayingGame(),
                TdApi.ChatActionWatchingAnimations("🎉"),
            ).map { it.toChatAction() }

        assertEquals(
            listOf(
                ChatAction.Typing,
                ChatAction.RecordingVoiceNote,
                ChatAction.UploadingVoiceNote(progress = 10),
                ChatAction.RecordingVideo,
                ChatAction.UploadingVideo(progress = 20),
                ChatAction.RecordingVideoNote,
                ChatAction.UploadingVideoNote(progress = 30),
                ChatAction.UploadingPhoto(progress = 40),
                ChatAction.UploadingDocument(progress = 50),
                ChatAction.ChoosingSticker,
                ChatAction.ChoosingLocation,
                ChatAction.ChoosingContact,
                ChatAction.PlayingGame,
                ChatAction.WatchingAnimations(emoji = "🎉"),
            ),
            actions,
        )
    }

    @Test
    fun `a cancellation maps to no action`() {
        assertNull(TdApi.ChatActionCancel().toChatAction())
    }
}
