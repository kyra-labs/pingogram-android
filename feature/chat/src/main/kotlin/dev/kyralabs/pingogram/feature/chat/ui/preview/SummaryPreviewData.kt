package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.MessageAction
import dev.kyralabs.pingogram.feature.chat.ui.MessageActionsSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.translation.BubbleTranslation
import dev.kyralabs.pingogram.feature.chat.ui.translation.withTranslations
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Telegram's summaries of long posts for previews and screenshot tests (`ChatSummaries`): a channel whose older long
 * post shows its summary, the newest still being summarized, and the actions of a long post with "Summarize".
 */
internal object SummaryPreviewData {
    /** A channel of long posts, the older one summarized, the newest on its way. */
    val summarized: ChatUiState
        get() =
            ChatUiState(
                header =
                    ChatHeader(
                        chatId = CHANNEL,
                        title = UiText.Plain("Field Notes"),
                        photo = null,
                        placeholderIcon = null,
                        subtitle = ChatSubtitle.Status(UiText.Plain("12,480 subscribers")),
                    ),
                content =
                    content(
                        bubble(
                            NEWEST,
                            "This week the team moved the field station to the north ridge. The road was closed " +
                                "for two days after the storm, so the move took longer than planned, and the old " +
                                "station stays open until the new one has power.",
                            "9:48 AM",
                            position = GroupPosition.Last,
                        ),
                        bubble(
                            NEWEST - 1,
                            "Long read: what we learned from a year of measuring snow on the ridge.",
                            "Yesterday",
                            position = GroupPosition.First,
                        ),
                    ),
            ).withTranslations(
                persistentMapOf(
                    NEWEST to BubbleTranslation.Summarizing,
                    NEWEST - 1 to
                        BubbleTranslation.Summary(
                            FormattedText(
                                "A year of snow data shows the ridge melts two weeks earlier than ten years ago.",
                                persistentListOf(),
                            ),
                        ),
                ),
            )

    /** The actions of a long post: "Summarize" after copying, then "Translate". */
    val actions =
        MessageActionsSheet(
            messageId = NEWEST,
            reactionsMessageId = NEWEST,
            reactions = persistentListOf(),
            actions =
                persistentListOf(
                    MessageAction.Copy,
                    MessageAction.Translate,
                    MessageAction.Summarize,
                    MessageAction.CopyLink,
                    MessageAction.Forward,
                    MessageAction.Report,
                ),
        )

    private const val CHANNEL = -1_001_000_000_300L
    private const val NEWEST = 96L
}
