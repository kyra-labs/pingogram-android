package dev.kyralabs.pingogram.feature.chat.ui.export

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.feature.chat.R

// The words of the export page's choices.

@Composable
internal fun sizeLimitText(step: Int): String {
    val bytes = ExportSizeLimits[step]
    return if (bytes == ChatExportOptions.NO_SIZE_LIMIT) {
        stringResource(R.string.chat_export_size_any)
    } else {
        stringResource(R.string.chat_export_size_up_to, Formatter.formatShortFileSize(LocalContext.current, bytes))
    }
}

internal fun ExportFormat.labelRes(): Int =
    when (this) {
        ExportFormat.Html -> R.string.chat_export_format_html
        ExportFormat.Json -> R.string.chat_export_format_json
    }

internal fun ExportFormat.summaryRes(): Int =
    when (this) {
        ExportFormat.Html -> R.string.chat_export_format_html_summary
        ExportFormat.Json -> R.string.chat_export_format_json_summary
    }

internal fun ExportMediaKind.labelRes(): Int =
    when (this) {
        ExportMediaKind.Photos -> R.string.chat_export_media_photos
        ExportMediaKind.Videos -> R.string.chat_export_media_videos
        ExportMediaKind.VoiceMessages -> R.string.chat_export_media_voice
        ExportMediaKind.Files -> R.string.chat_export_media_files
    }

internal fun ExportMediaKind.summaryRes(): Int? =
    when (this) {
        ExportMediaKind.Videos -> R.string.chat_export_media_videos_summary
        ExportMediaKind.Files -> R.string.chat_export_media_files_summary
        ExportMediaKind.Photos, ExportMediaKind.VoiceMessages -> null
    }
