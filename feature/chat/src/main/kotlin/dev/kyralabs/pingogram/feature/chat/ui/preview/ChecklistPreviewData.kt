package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import dev.kyralabs.pingogram.feature.chat.ui.checklist.ChecklistUi
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.today
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Checklists for previews and screenshot tests (`REQUIREMENTS.md` FR-C4, the owner's board 1): a group's conversation
 * with the user's own checklist (two of five tasks done, ticked by Ada) over one Ada sent (one task the user ticked,
 * "Add task" offered), and the same conversation while a task's change is on its way, the inline field is open and a
 * checklist nobody else may tick.
 */
internal object ChecklistPreviewData {
    private const val GROUP = -300L
    private const val ADA = ContentPreviewData.CHECKLIST_COMPLETER
    private const val ME = 1L

    private val ada = SenderAvatar(ADA, UiText.Plain("Ada Lovelace"), photo = null, placeholderIcon = null)
    private val me = SenderAvatar(ME, UiText.resource(UiR.string.preview_sender_you), photo = null, placeholderIcon = null)

    private val header =
        ChatHeader(
            chatId = GROUP,
            title = UiText.Plain("Kyra Labs Core"),
            photo = null,
            placeholderIcon = null,
            subtitle = ChatSubtitle.Status(UiText.plural(R.plurals.chat_subtitle_members, 6, 6)),
        )

    /** The user's checklist with Ada's two ticks, over Ada's checklist with one task the user ticked. */
    val lists: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header,
                content =
                    content(
                        checklist(
                            3,
                            ContentPreviewData.checklist(done = 1, release = false, completerId = ME),
                            ui = ChecklistUi(completers = persistentMapOf(1 to me)),
                            sender = "Ada Lovelace",
                            senderId = ADA,
                        ),
                        checklist(
                            2,
                            ContentPreviewData.checklist(done = 2),
                            ui = ChecklistUi(completers = persistentMapOf(1 to ada, 2 to ada)),
                            isOutgoing = true,
                        ),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    /** A task's change on its way and the inline field open, over a checklist the user may neither tick nor add to. */
    val states: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header = header,
                content =
                    content(
                        checklist(
                            3,
                            ContentPreviewData.checklist(done = 1, release = false, completerId = ME),
                            ui =
                                ChecklistUi(
                                    completers = persistentMapOf(1 to me),
                                    pending = persistentSetOf(2),
                                    isAddingTask = true,
                                ),
                            sender = "Ada Lovelace",
                            senderId = ADA,
                        ),
                        checklist(
                            2,
                            ContentPreviewData.checklist(done = 2, canMark = false, canAdd = false),
                            ui = ChecklistUi(completers = persistentMapOf(1 to ada, 2 to ada)),
                            sender = "Grace Hopper",
                            senderId = 43,
                        ),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    private fun checklist(
        id: Long,
        content: MessageContent.Checklist,
        ui: ChecklistUi,
        sender: String = "",
        senderId: Long = 0,
        isOutgoing: Boolean = false,
    ): ChatItem.Bubble =
        bubble(
            id = id,
            text = "",
            time = "10:4$id AM",
            isOutgoing = isOutgoing,
            sender = if (isOutgoing) UiText.resource(UiR.string.preview_sender_you) else UiText.Plain(sender),
            senderId = senderId,
            groupChat = true,
            position = GroupPosition.Single,
            status = if (isOutgoing) OutgoingStatus.Read else null,
            content = content,
        ).copy(checklist = ui)
}
