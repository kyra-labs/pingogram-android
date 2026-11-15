package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.transcription.BubbleTranscript
import dev.kyralabs.pingogram.feature.chat.ui.transcription.withTranscripts
import kotlinx.collections.immutable.persistentMapOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Voice message transcription for previews and screenshot tests (`docs/DESIGN_SYSTEM.md` §6, the owner's board 28):
 * the Transcribe button, a transcript with "On this phone" and one being made ([transcribing]); a long transcript cut
 * with "Show more", a language the device has not, and a failure ([states]); round video messages ([roundVideos]).
 */
internal object TranscriptionPreviewData {
    private val ada = UiText.Plain("Meera")

    private fun chat(vararg bubbles: dev.kyralabs.pingogram.feature.chat.ui.ChatItem.Bubble) =
        ChatUiState(
            header =
                ChatHeader(
                    chatId = CHAT,
                    title = UiText.Plain("Meera Iyer"),
                    photo = null,
                    placeholderIcon = null,
                    subtitle = ChatSubtitle.Status(UiText.resource(UiR.string.user_status_online)),
                ),
            content = content(*bubbles),
            canTranscribe = true,
        )

    private fun voice(
        id: Long,
        time: String,
        isOutgoing: Boolean = false,
    ) = bubble(
        id = id,
        text = "",
        time = time,
        isOutgoing = isOutgoing,
        sender = ada,
        content = ContentPreviewData.voiceNote(isListened = isOutgoing),
        status = if (isOutgoing) OutgoingStatus.Read else null,
    )

    /** The button beside a voice message, a transcript with "On this phone", and one being made. */
    val transcribing: ChatUiState
        get() =
            chat(
                voice(4, "6:07 PM"),
                voice(3, "6:05 PM", isOutgoing = true),
                voice(2, "6:02 PM"),
            ).withTranscripts(
                persistentMapOf(
                    4L to BubbleTranscript.Transcribing,
                    3L to
                        BubbleTranscript.Shown(
                            "Reached the station, platform 4. Train’s on time, so I’ll be at yours by eight. " +
                                "Save me some chai!",
                            showsNotice = true,
                        ),
                ),
                canTranscribe = true,
            )

    /** A long transcript cut with "Show more", a language the device has not, and a failure. */
    val states: ChatUiState
        get() =
            chat(
                voice(7, "5:58 PM", isOutgoing = true),
                voice(6, "5:52 PM"),
                voice(5, "5:40 PM"),
            ).withTranscripts(
                persistentMapOf(
                    7L to BubbleTranscript.Failed,
                    6L to BubbleTranscript.LanguageUnavailable,
                    5L to BubbleTranscript.Shown(LONG),
                ),
                canTranscribe = true,
            )

    /** An incoming round video message transcribed automatically, and the user's own with the button. */
    val roundVideos: ChatUiState
        get() =
            chat(
                bubble(
                    id = 9,
                    text = "",
                    time = "6:41 PM",
                    isOutgoing = true,
                    content = ContentPreviewData.videoNote(minithumbnail = null, isViewed = true),
                    status = OutgoingStatus.Read,
                ),
                bubble(
                    id = 8,
                    text = "",
                    time = "6:30 AM",
                    sender = UiText.Plain("Rahul"),
                    content = ContentPreviewData.videoNote(minithumbnail = null, isViewed = false),
                ),
            ).withTranscripts(
                persistentMapOf(
                    8L to
                        BubbleTranscript.Shown(
                            "Look at this view! You can see all the way to the next hill. " +
                                "Totally worth the early start.",
                            isAutomatic = true,
                        ),
                ),
                canTranscribe = true,
            )

    private const val CHAT = 404L

    private const val LONG =
        "Okay so here’s the plan for Saturday. We leave at six from Yeshwantpur, breakfast at the usual place near " +
            "Mandya, then straight to Raja’s Seat for the view before it gets crowded. After lunch we can split — " +
            "anyone who wants the coffee estate tour goes with Rahul, the rest of us do the waterfall."
}
