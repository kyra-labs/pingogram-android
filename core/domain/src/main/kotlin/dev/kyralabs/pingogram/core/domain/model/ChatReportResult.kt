package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * What came of reporting messages of a chat to Telegram's moderators (`MessageActionRepository.report`, TDLib's
 * `reportChatResult`): done, or the next step it asks the user for.
 */
sealed interface ChatReportResult {
    /** Telegram has the report. */
    data object Reported : ChatReportResult

    /** Report again with one of [options]: [title] asks why. */
    data class OptionRequired(
        val title: String,
        val options: ImmutableList<ReportOption>,
    ) : ChatReportResult

    /**
     * Report again with [option] and a comment that says more, which may be left empty if [isOptional]. The option is
     * TDLib's, which may differ from the one the user chose.
     */
    data class TextRequired(
        val option: ReportOptionId,
        val isOptional: Boolean,
    ) : ChatReportResult

    /** Telegram wants the messages the report is about: reporting messages never gets here. */
    data object MessagesRequired : ChatReportResult
}
