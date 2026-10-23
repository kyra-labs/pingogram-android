package dev.kyralabs.pingogram.feature.chat.ui.export.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.export.ChatExportUiState
import dev.kyralabs.pingogram.feature.chat.ui.export.ExportForm
import dev.kyralabs.pingogram.feature.chat.ui.export.ExportRun
import kotlinx.collections.immutable.persistentSetOf

/** The export page's states for previews and screenshot tests. */
internal object ChatExportPreviewData {
    private val title = UiText.Plain("Ada Lovelace")

    /** The form, with photos and voice messages chosen, a limit of 50 MB, a day and a folder. */
    val form =
        ChatExportUiState.Content(
            chatTitle = title,
            form =
                ExportForm(
                    format = ExportFormat.Html,
                    media = persistentSetOf(ExportMediaKind.Photos, ExportMediaKind.VoiceMessages),
                    sizeLimit = 2,
                    sinceDay = 20_362,
                    folderName = "Download",
                ),
        )

    /** The form as the page opens: text only, all messages, no folder. */
    val emptyForm = ChatExportUiState.Content(chatTitle = title, form = ExportForm())

    val running =
        ChatExportUiState.Content(
            chatTitle = title,
            form = ExportForm(),
            run = ExportRun.Running(ChatExportProgress(messages = 1_234, fraction = 0.4f)),
        )

    val waiting =
        ChatExportUiState.Content(
            chatTitle = title,
            form = ExportForm(),
            run = ExportRun.Running(ChatExportProgress(messages = 300, floodWaitSeconds = 12)),
        )

    val done =
        ChatExportUiState.Content(
            chatTitle = title,
            form = ExportForm(),
            run =
                ExportRun.Done(
                    ChatExportResult(messageCount = 1_234, mediaCount = 56, skippedMediaCount = 12, folderUri = ""),
                ),
        )

    val failed =
        ChatExportUiState.Content(
            chatTitle = title,
            form = ExportForm(),
            run = ExportRun.Failed(ChatExportError.StorageFull),
        )

    val networkFailed =
        ChatExportUiState.Content(
            chatTitle = title,
            form = ExportForm(),
            run = ExportRun.Failed(ChatExportError.Telegram(DomainError.Network)),
        )
}
