package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.feature.chat.ui.MessageAction
import dev.kyralabs.pingogram.feature.chat.ui.MessageSelection
import dev.kyralabs.pingogram.feature.chat.ui.ReportSheet
import dev.kyralabs.pingogram.feature.chat.ui.ReportStep
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** Selection mode and reporting messages, for previews and screenshot tests. */
internal object SelectionPreviewData {
    /**
     * [ChatPreviewData.privateChat] in selection mode: Ada's two messages and the user's first one of today selected,
     * with everything in the toolbar.
     */
    val selecting =
        ChatPreviewData.privateChat.copy(
            selection =
                MessageSelection(
                    messageIds = persistentSetOf(6, 10, 11),
                    actions =
                        persistentListOf(
                            MessageAction.Copy,
                            MessageAction.Forward,
                            MessageAction.Report,
                            MessageAction.Delete,
                        ),
                ),
        )

    /** The report of Ada's messages at its reasons, as Telegram words them. */
    val reportOptions =
        ReportSheet(
            ReportStep.Options(
                title = "Report messages",
                options =
                    persistentListOf(
                        "I don't like it",
                        "Child abuse",
                        "Violence",
                        "Illegal goods and services",
                        "Illegal adult content",
                        "Personal data",
                        "Terrorism",
                        "Scam or spam",
                        "Copyright",
                        "Other",
                    ),
            ),
        )

    /** [selecting] reporting the selected messages. */
    val reporting = selecting.copy(report = reportOptions)
}
