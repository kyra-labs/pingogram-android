package dev.kyralabs.pingogram.feature.settings.ui

import android.content.res.Resources
import android.icu.text.ListFormatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSizeLimits
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The automatic download page of [network]: hoists [AutoDownloadViewModel] and shows why a change could not be saved
 * in a snackbar. [onBack] leaves the page.
 */
@Composable
internal fun AutoDownloadRoute(
    network: AutoDownloadNetwork,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutoDownloadViewModel =
        hiltViewModel<AutoDownloadViewModel, AutoDownloadViewModel.Factory>(key = "auto-download-$network") {
            it.create(network)
        },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is AutoDownloadEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    AutoDownloadScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * What downloads by itself on one kind of network (`docs/PERFORMANCE.md` §7): a switch each for photos, videos and
 * GIFs, files and voice and video messages, videos and files with a size limit below their switch, an expressive
 * [Slider] through [AutoDownloadSizeLimits] that ticks at each step (`SegmentTick`) and is disabled while its switch is
 * off. While Data Saver holds the network back, a note on top says that nothing downloads by itself meanwhile. A
 * footer says what always downloads. Nothing shows until the settings are read, a matter of milliseconds.
 */
@Composable
internal fun AutoDownloadScreen(
    state: AutoDownloadUiState,
    onEvent: (AutoDownloadEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(state.network.titleRes()),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        val rules = state.rules ?: return@SettingsPageScaffold
        if (state.isDataSaverOn) {
            SettingsGroup {
                item { shapes ->
                    SettingsRow(
                        title = stringResource(R.string.settings_auto_download_data_saver),
                        shapes = shapes,
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_auto_download_data_saver_summary),
                            )
                        },
                    )
                }
            }
        }
        val change = { changed: AutoDownloadRules -> onEvent(AutoDownloadEvent.Change(changed)) }
        SettingsGroup(
            title = stringResource(R.string.settings_auto_download_section),
            footer = stringResource(R.string.settings_auto_download_footer),
        ) {
            item(key = PHOTOS) { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_auto_download_photos),
                    summary = stringResource(R.string.settings_auto_download_photos_summary),
                    checked = rules.photos,
                    shapes = shapes,
                    onCheckedChange = { change(rules.copy(photos = it)) },
                )
            }
            item(key = VIDEOS) { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_auto_download_videos),
                    summary = stringResource(R.string.settings_auto_download_videos_summary),
                    checked = rules.videos,
                    shapes = shapes,
                    onCheckedChange = { change(rules.copy(videos = it)) },
                )
            }
            item(key = VIDEO_LIMIT) { shapes ->
                SizeLimitSegment(
                    title = stringResource(R.string.settings_auto_download_video_limit),
                    limit = rules.videoSizeLimit,
                    enabled = rules.videos,
                    shapes = shapes,
                    onChange = { change(rules.copy(videoSizeLimit = it)) },
                )
            }
            item(key = FILES) { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_auto_download_files),
                    summary = stringResource(R.string.settings_auto_download_files_summary),
                    checked = rules.files,
                    shapes = shapes,
                    onCheckedChange = { change(rules.copy(files = it)) },
                )
            }
            item(key = FILE_LIMIT) { shapes ->
                SizeLimitSegment(
                    title = stringResource(R.string.settings_auto_download_file_limit),
                    limit = rules.fileSizeLimit,
                    enabled = rules.files,
                    shapes = shapes,
                    onChange = { change(rules.copy(fileSizeLimit = it)) },
                )
            }
            item(key = VOICE) { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_auto_download_voice),
                    summary = stringResource(R.string.settings_auto_download_voice_summary),
                    checked = rules.voiceAndVideoMessages,
                    shapes = shapes,
                    onCheckedChange = { change(rules.copy(voiceAndVideoMessages = it)) },
                )
            }
        }
    }
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

@PingogramPreviews
@Composable
private fun AutoDownloadScreenPreview() {
    PingogramPreview { AutoDownloadScreen(state = SettingsPreviewData.autoDownloadWiFi, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun AutoDownloadScreenDataSaverPreview() {
    PingogramPreview {
        AutoDownloadScreen(state = SettingsPreviewData.autoDownloadMobileDataSaver, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun AutoDownloadScreenLoadingPreview() {
    PingogramPreview {
        AutoDownloadScreen(state = AutoDownloadUiState(AutoDownloadNetwork.Roaming), onEvent = {}, onBack = {})
    }
}
