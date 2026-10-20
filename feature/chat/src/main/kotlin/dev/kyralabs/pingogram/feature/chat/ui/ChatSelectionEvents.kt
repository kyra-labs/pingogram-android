package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatForwarding

/**
 * The events of selection mode, reporting and saving (`REQUIREMENTS.md` FR-C6 "select, report, save to downloads")
 * for `ChatViewModel`, handed to the [selection], the [reporting], the [saving] and the [messageActions] that check
 * them against TDLib. The selection toolbar's actions run on every selected message, oldest first: copying their texts
 * ends selection mode at once; forwarding (through [forwarding]), reporting and deleting once they are done. The
 * messages that waited for the storage permission are found again in the [history].
 */
internal class ChatSelectionEvents(
    private val selection: ChatSelection,
    private val reporting: ChatReporting,
    private val saving: ChatSaving,
    private val messageActions: ChatMessageActions,
    private val forwarding: ChatForwarding,
    private val history: suspend () -> ChatHistory,
) {
    fun onEvent(event: SelectionEvent) {
        when (event) {
            is SelectionEvent.Select -> {
                messageActions.select(event.messageId)
            }

            is SelectionEvent.Toggle -> {
                selection.toggle(event.messageId)
            }

            SelectionEvent.Clear -> {
                selection.clear()
            }

            is SelectionEvent.Act -> {
                act(event.action)
            }

            is SelectionEvent.Report -> {
                messageActions.report(event.messageId)
            }

            is SelectionEvent.ChooseReportOption -> {
                reporting.choose(event.index)
            }

            SelectionEvent.SendReportComment -> {
                reporting.sendComment()
            }

            SelectionEvent.DismissReport -> {
                reporting.dismiss()
            }

            is SelectionEvent.SaveToDownloads -> {
                messageActions.saveToDownloads(event.messageId)
            }

            is SelectionEvent.StoragePermissionResult -> {
                saving.onPermissionResult(event.granted) { ids -> history().messages.filter { it.id in ids } }
            }
        }
    }

    /** The selection toolbar's [action] on the selected messages. */
    private fun act(action: MessageAction) =
        selection.perform(action) { context, messages, properties ->
            when (action) {
                MessageAction.Copy -> {
                    selection.copy(context, messages)
                }

                MessageAction.Forward -> {
                    forwarding.open(messages, onSent = selection::clear)
                }

                MessageAction.Report -> {
                    reporting.start(messages.map { it.id })
                }

                MessageAction.Delete -> {
                    val scope = context.deleteScope(properties)
                    messageActions.askDelete(messages.map { it.id }, scope, fromSelection = true)
                }

                // The toolbar offers nothing else.
                else -> {
                    return@perform
                }
            }
        }
}
