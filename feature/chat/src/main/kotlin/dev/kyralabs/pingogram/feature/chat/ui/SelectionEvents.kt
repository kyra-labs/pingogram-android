package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * What the user does to select, report and save messages (`REQUIREMENTS.md` FR-C6 "select, report, save to
 * downloads"); [ChatViewModel] hands these to [ChatSelection], [ChatReporting] and [ChatSaving].
 */
sealed interface SelectionEvent : ChatEvent {
    /** "Select" in message [messageId]'s actions: selection mode starts with it (an album's every item). */
    data class Select(
        val messageId: Long,
    ) : SelectionEvent

    /** In selection mode, message [messageId] was tapped or long-pressed: it is selected, or no longer is. */
    data class Toggle(
        val messageId: Long,
    ) : SelectionEvent

    /** The selection bar's close button, or back: selection mode ends. */
    data object Clear : SelectionEvent

    /** One of the selection toolbar's [MessageSelection.actions], for every selected message. */
    data class Act(
        val action: MessageAction,
    ) : SelectionEvent

    /** "Report" in message [messageId]'s actions (an album's every item). */
    data class Report(
        val messageId: Long,
    ) : SelectionEvent

    /** Report again with the option at [index] of the report's [ReportStep.Options]. */
    data class ChooseReportOption(
        val index: Int,
    ) : SelectionEvent

    /** Send the report with the comment written for its [ReportStep.Comment]. */
    data object SendReportComment : SelectionEvent

    /** The report sheet was closed: nothing more is reported. */
    data object DismissReport : SelectionEvent

    /** "Save to Downloads" in message [messageId]'s actions (an album's every file). */
    data class SaveToDownloads(
        val messageId: Long,
    ) : SelectionEvent

    /**
     * The system's answer to the storage permission that saving needs on Android 8–9
     * ([SelectionEffect.RequestStoragePermission]).
     */
    data class StoragePermissionResult(
        val granted: Boolean,
    ) : SelectionEvent
}

/** One-off effects of selecting, reporting and saving messages, handled by the screen once each. */
sealed interface SelectionEffect : ChatEffect {
    /**
     * Put the texts of several [messages] on the clipboard, oldest first, each under its sender and when it was sent,
     * as plain text. Android 13 and later confirm it themselves; earlier versions get a snackbar.
     */
    data class CopyMessages(
        val messages: ImmutableList<CopiedMessage>,
    ) : SelectionEffect {
        override fun toString() = "CopyMessages(${messages.size})"
    }

    /** Telegram's moderators have the report: a snackbar thanks the user. */
    data object Reported : SelectionEffect

    /**
     * Saving needs the storage permission (Android 8–9): the screen explains why first where the system wants that,
     * asks for it, and reports the answer with [SelectionEvent.StoragePermissionResult].
     */
    data object RequestStoragePermission : SelectionEffect

    /** The storage permission was refused: a snackbar says that saving needs it. */
    data object StoragePermissionDenied : SelectionEffect

    /** A file to save is downloaded first: a snackbar says it is on its way. */
    data object Saving : SelectionEffect

    /**
     * [count] files are in Downloads now: a snackbar says so, with an action that opens the one file saved if there is
     * one ([open]).
     */
    data class Saved(
        val count: Int,
        val open: ChatEffect.OpenFile? = null,
    ) : SelectionEffect

    /** No file could be saved: a snackbar says so. */
    data object SaveFailed : SelectionEffect
}

/** A message's text as copied with others: under its [sender] and when it was sent ([date], Unix time in seconds). */
@Immutable
data class CopiedMessage(
    val sender: UiText,
    val date: Long,
    val text: String,
) {
    override fun toString() = "CopiedMessage(date=$date, …)"
}
