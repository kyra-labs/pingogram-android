package dev.kyralabs.pingogram.feature.settings.ui

import android.content.res.Resources
import android.icu.text.ListFormatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSizeLimits
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import java.text.NumberFormat
import kotlin.math.roundToInt

// The rows of automatic download (docs/PERFORMANCE.md §7): Data and storage's group of networks, and a network's page.

/**
 * "Automatic download": a row per kind of network that says what downloads by itself on it and opens its page, and,
 * while Data Saver is on, a note that nothing does on mobile data meanwhile.
 */
@Composable
internal fun AutoDownloadGroup(
    settings: AutoDownloadSettings,
    isDataSaverOn: Boolean,
    onOpen: (AutoDownloadNetwork) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_section_auto_download),
        footer = if (isDataSaverOn) stringResource(R.string.settings_auto_download_data_saver_footer) else null,
    ) {
        for (network in AutoDownloadNetwork.entries) {
            item(key = network) { shapes ->
                PageItem(
                    title = stringResource(network.titleRes()),
                    summary = autoDownloadSummary(settings.rulesFor(network)),
                    icon = network.iconRes(),
                    shapes = shapes,
                    onClick = { onOpen(network) },
                )
            }
        }
    }
}

@DrawableRes
private fun AutoDownloadNetwork.iconRes(): Int =
    when (this) {
        AutoDownloadNetwork.Mobile -> R.drawable.ic_signal_cellular_alt
        AutoDownloadNetwork.WiFi -> R.drawable.ic_wifi
        AutoDownloadNetwork.Roaming -> R.drawable.ic_public
    }

/**
 * The switches and size limits of [rules], each change going to [change] as the rules it makes, under "Download
 * automatically", with a footer that says what always downloads.
 */
@Composable
internal fun RulesGroup(
    rules: AutoDownloadRules,
    change: (AutoDownloadRules) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_auto_download_section),
        footer = stringResource(R.string.settings_auto_download_footer),
    ) {
        switch(
            key = PHOTOS,
            title = R.string.settings_auto_download_photos,
            summary = R.string.settings_auto_download_photos_summary,
            checked = rules.photos,
        ) { change(rules.copy(photos = it)) }
        switch(
            key = VIDEOS,
            title = R.string.settings_auto_download_videos,
            summary = R.string.settings_auto_download_videos_summary,
            checked = rules.videos,
        ) { change(rules.copy(videos = it)) }
        sizeLimit(VIDEO_LIMIT, R.string.settings_auto_download_video_limit, rules.videoSizeLimit, rules.videos) {
            change(rules.copy(videoSizeLimit = it))
        }
        switch(
            key = FILES,
            title = R.string.settings_auto_download_files,
            summary = R.string.settings_auto_download_files_summary,
            checked = rules.files,
        ) { change(rules.copy(files = it)) }
        sizeLimit(FILE_LIMIT, R.string.settings_auto_download_file_limit, rules.fileSizeLimit, rules.files) {
            change(rules.copy(fileSizeLimit = it))
        }
        switch(
            key = VOICE,
            title = R.string.settings_auto_download_voice,
            summary = R.string.settings_auto_download_voice_summary,
            checked = rules.voiceAndVideoMessages,
        ) { change(rules.copy(voiceAndVideoMessages = it)) }
    }
}

/** A row of the group with a [SizeLimitSegment] of [title], under [key]. */
private fun SettingsGroupScope.sizeLimit(
    key: String,
    @StringRes title: Int,
    limit: Long,
    enabled: Boolean,
    onChange: (Long) -> Unit,
) = item(key = key) { shapes ->
    SizeLimitSegment(stringResource(title), limit, enabled, shapes, onChange)
}

/** A row of the group with a [SwitchItem] of [title] and [summary], under [key]. */
private fun SettingsGroupScope.switch(
    key: String,
    @StringRes title: Int,
    @StringRes summary: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = item(key = key) { shapes ->
    SwitchItem(
        title = stringResource(title),
        summary = stringResource(summary),
        checked = checked,
        shapes = shapes,
        onCheckedChange = onCheckedChange,
    )
}

/**
 * A size limit, a row of its group ([shapes]): [title] and the limit ("Up to 10 MB") in a list item, and below them a
 * [Slider] through [AutoDownloadSizeLimits], which calls [onChange] with the limit of each step it reaches and ticks
 * there. A limit between two steps shows at the next larger one. Screen readers hear the slider by [title] with the
 * limit as its state.
 */
@Composable
private fun SizeLimitSegment(
    title: String,
    limit: Long,
    enabled: Boolean,
    shapes: ListItemShapes,
    onChange: (Long) -> Unit,
) {
    val index = sizeLimitIndex(limit)
    val limitText = stringResource(R.string.settings_auto_download_up_to, sizeText(LocalResources.current, limit))
    val haptics = LocalHapticFeedback.current
    SettingsSegment(shapes) {
        SegmentedListItem(
            shapes = shapes,
            enabled = enabled,
            supportingContent = { Text(limitText) },
            colors = nestedItemColors(),
        ) {
            Text(title)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { value ->
                val step = value.roundToInt().coerceIn(AutoDownloadSizeLimits.indices)
                if (step != index) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onChange(AutoDownloadSizeLimits[step])
                }
            },
            modifier =
                Modifier
                    .padding(
                        start = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.small,
                    ).semantics {
                        contentDescription = title
                        stateDescription = limitText
                    },
            enabled = enabled,
            valueRange = 0f..AutoDownloadSizeLimits.lastIndex.toFloat(),
            steps = AutoDownloadSizeLimits.size - 2,
        )
    }
}

/** The step of [AutoDownloadSizeLimits] that shows [limit]: the smallest at least as large, else the largest. */
internal fun sizeLimitIndex(limit: Long): Int =
    AutoDownloadSizeLimits.indexOfFirst { it >= limit }.takeIf { it >= 0 } ?: AutoDownloadSizeLimits.lastIndex

/**
 * A size limit in whole megabytes, or gigabytes from 1 GB, as decimal units like Android's and the bubbles' sizes:
 * "10 MB", "2 GB" (Android's short format would say "3.0 MB").
 */
private fun sizeText(
    resources: Resources,
    bytes: Long,
): String {
    val locale = resources.configuration.locales[0]
    val gigabytes = bytes >= BYTES_PER_GIGABYTE
    val value = (bytes.toDouble() / if (gigabytes) BYTES_PER_GIGABYTE else BYTES_PER_MEGABYTE)
    val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(value)
    return resources.getString(
        if (gigabytes) R.string.settings_size_gigabytes else R.string.settings_size_megabytes,
        number,
    )
}

private const val BYTES_PER_MEGABYTE = 1_000_000L
private const val BYTES_PER_GIGABYTE = 1_000_000_000L

/**
 * What [rules] download by themselves, for the network's row on Data and storage: "Photos, Videos up to 10 MB and
 * Files up to 3 MB", in the language's own list format, or "Nothing".
 */
@Composable
internal fun autoDownloadSummary(rules: AutoDownloadRules): String {
    val resources = LocalResources.current
    val parts =
        buildList {
            if (rules.photos) add(stringResource(R.string.settings_auto_download_list_photos))
            if (rules.videos) {
                add(
                    stringResource(
                        R.string.settings_auto_download_list_videos,
                        sizeText(resources, rules.videoSizeLimit),
                    ),
                )
            }
            if (rules.files) {
                add(
                    stringResource(
                        R.string.settings_auto_download_list_files,
                        sizeText(resources, rules.fileSizeLimit),
                    ),
                )
            }
            if (rules.voiceAndVideoMessages) add(stringResource(R.string.settings_auto_download_list_voice))
        }
    if (parts.isEmpty()) return stringResource(R.string.settings_auto_download_nothing)
    val locale = LocalConfiguration.current.locales[0]
    return remember(parts, locale) { ListFormatter.getInstance(locale).format(parts) }
}

@StringRes
internal fun AutoDownloadNetwork.titleRes(): Int =
    when (this) {
        AutoDownloadNetwork.Mobile -> R.string.settings_auto_download_mobile
        AutoDownloadNetwork.WiFi -> R.string.settings_auto_download_wifi
        AutoDownloadNetwork.Roaming -> R.string.settings_auto_download_roaming
    }

private const val PHOTOS = "photos"
private const val VIDEOS = "videos"
private const val VIDEO_LIMIT = "videoLimit"
private const val FILES = "files"
private const val FILE_LIMIT = "fileLimit"
private const val VOICE = "voice"
