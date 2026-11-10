package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.PollLimits
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachPreview
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollClosing
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollDraft
import dev.kyralabs.pingogram.feature.chat.ui.attach.PreviewItem
import kotlinx.collections.immutable.persistentListOf

/**
 * Sample attach previews and poll drafts for previews and screenshot tests: picked photos and a video with one item
 * still being prepared, picked files, and a quiz being written. The paths name no real files: previews show
 * [dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages]' colors for them.
 */
internal object AttachPreviewData {
    val media =
        AttachPreview(
            kind = AttachmentKind.Media,
            items =
                persistentListOf(
                    PreviewItem.Ready(0, Attachment.Photo("/cache/uploads/a/photo1.jpg", 812_000, 2560, 1920)),
                    PreviewItem.Ready(1, Attachment.Photo("/cache/uploads/b/photo2.jpg", 640_000, 1920, 2560)),
                    PreviewItem.Ready(
                        2,
                        Attachment.Video(
                            path = "/cache/uploads/c/clip.mp4",
                            size = 12_400_000,
                            width = 1920,
                            height = 1080,
                            duration = 42,
                            thumbnail = AttachmentThumbnail("/cache/uploads/c/thumbnail.jpg", 320, 180),
                        ),
                    ),
                    PreviewItem.Preparing(3),
                ),
        )

    /** A photo picked to replace the media of the message being edited. */
    val replacement =
        AttachPreview(
            kind = AttachmentKind.Media,
            items =
                persistentListOf(
                    PreviewItem.Ready(0, Attachment.Photo("/cache/uploads/f/sunset.jpg", 920_000, 2560, 1920)),
                ),
            isReplacement = true,
        )

    val files =
        AttachPreview(
            kind = AttachmentKind.Document,
            items =
                persistentListOf(
                    PreviewItem.Ready(
                        0,
                        Attachment.Document(
                            path = "/cache/uploads/d/Q3 report.pdf",
                            size = 2_450_000,
                            fileName = "Q3 report.pdf",
                            mimeType = "application/pdf",
                        ),
                    ),
                    PreviewItem.Ready(
                        1,
                        Attachment.Document(
                            path = "/cache/uploads/e/Meeting notes from the planning session in October.docx",
                            size = 48_200,
                            fileName = "Meeting notes from the planning session in October.docx",
                            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        ),
                    ),
                    PreviewItem.Ready(
                        2,
                        Attachment.Document("/cache/uploads/f/photos.zip", 31_000_000, "photos.zip", ""),
                    ),
                ),
        )

    /** A quiz with its correct answer chosen. */
    val quiz =
        PollDraft(
            question = "Which planet is the largest?",
            options = listOf("Mars", "Jupiter", "Venus"),
            isAnonymous = true,
            isQuiz = true,
            correctOptionIndexes = listOf(1),
            explanation = "Jupiter is more than twice as heavy as all the other planets together.",
        )

    /** Board 5a's poll: a photo, a description, four options, one with a link, closing in a day. */
    val bookPoll =
        PollDraft(
            question = "Which book should we read in December?",
            options = listOf("Tomorrow and Tomorrow and Tomorrow", "The Ministry for the Future", "Piranesi", "Klara and the Sun"),
            links = listOf(null, "https://www.goodreads.com/book/show/50998056", null, null),
            description = "We meet on Dec 14 at Matteo. Reviews are linked where I found them.",
            photo = Attachment.Photo("/cache/uploads/c/poll.jpg", 1_258_291, 1280, 853),
            isAnonymous = false,
            allowsMultipleAnswers = true,
            hidesResults = true,
            closing = PollClosing.Day,
        )

    /** What Telegram allows, as it says in 2026. */
    val limits = PollLimits(maxOptions = 12, maxOpenPeriod = 2_678_400)
}
