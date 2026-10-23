package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** What the export page shows (`REQUIREMENTS.md` §5.9, `docs/DESIGN_SYSTEM.md` §6 "Chat export"). */
@Immutable
sealed interface ChatExportUiState {
    /** The chat is being read; a delayed loading indicator. */
    data object Loading : ChatExportUiState

    /** The chat could not be read, with "Try again". */
    data class Error(
        val error: DomainError,
    ) : ChatExportUiState

    /** The chat cannot be exported, and why: a secret chat, or one that restricts saving its content. */
    data class NotAllowed(
        val isSecretChat: Boolean,
    ) : ChatExportUiState

    /** The page proper: [form] while no export of this page is shown, else [run]. */
    data class Content(
        val chatTitle: UiText,
        val form: ExportForm,
        val run: ExportRun? = null,
    ) : ChatExportUiState {
        override fun toString() = "Content(form=$form, run=$run, …)"
    }
}

/**
 * What the user chose.
 *
 * @property sizeLimit the step of [ExportSizeLimits] files may reach; the last copies every file.
 * @property sinceDay only messages from this day on (days since 1970-01-01, the user's time zone); null for all.
 * @property folderName the folder picked; null until one is.
 * @property isPickingFolder the folder picked is being checked.
 */
@Immutable
data class ExportForm(
    val format: ExportFormat = ExportFormat.Html,
    val media: ImmutableSet<ExportMediaKind> = persistentSetOf(),
    val sizeLimit: Int = ExportSizeLimits.lastIndex,
    val sinceDay: Long? = null,
    val folderName: String? = null,
    val isPickingFolder: Boolean = false,
) {
    /** "Export" is possible: a folder was picked. */
    val canExport: Boolean get() = folderName != null && !isPickingFolder

    override fun toString() =
        "ExportForm(format=$format, media=$media, sizeLimit=$sizeLimit, since=${sinceDay != null}, …)"
}

/** The export this page started, as it runs and once it ended. */
@Immutable
sealed interface ExportRun {
    data class Running(
        val progress: ChatExportProgress,
    ) : ExportRun

    data class Done(
        val result: ChatExportResult,
    ) : ExportRun

    data class Failed(
        val error: ChatExportError,
    ) : ExportRun
}

/** The size limits the media slider goes through, in bytes; the last is no limit. */
val ExportSizeLimits =
    persistentListOf(
        1_000_000L,
        10_000_000L,
        50_000_000L,
        100_000_000L,
        500_000_000L,
        ChatExportOptions.NO_SIZE_LIMIT,
    )

sealed interface ChatExportEvent {
    data class SetFormat(
        val format: ExportFormat,
    ) : ChatExportEvent

    data class SetMedia(
        val kind: ExportMediaKind,
        val included: Boolean,
    ) : ChatExportEvent

    data class SetSizeLimit(
        val step: Int,
    ) : ChatExportEvent

    /** The whole history. */
    data object AllMessages : ChatExportEvent

    /** From [day] on (days since 1970-01-01). */
    data class Since(
        val day: Long,
    ) : ChatExportEvent

    /** The Storage Access Framework's picker returned folder [treeUri]. */
    data class FolderPicked(
        val treeUri: String,
    ) : ChatExportEvent {
        override fun toString() = "FolderPicked(…)"
    }

    /** Start the export; the notification names the chat [chatTitle], as the page shows it. */
    data class Export(
        val chatTitle: String,
    ) : ChatExportEvent {
        override fun toString() = "Export(…)"
    }

    data object Cancel : ChatExportEvent

    /** Back to the form after an export ended ("Export again", "Try again"). */
    data object Dismiss : ChatExportEvent

    /** Read the chat again after it failed. */
    data object Retry : ChatExportEvent
}

sealed interface ChatExportEffect {
    /** The folder picked cannot be used. */
    data object FolderUnusable : ChatExportEffect

    /** The export was cancelled, and what it wrote deleted. */
    data object Cancelled : ChatExportEffect
}
