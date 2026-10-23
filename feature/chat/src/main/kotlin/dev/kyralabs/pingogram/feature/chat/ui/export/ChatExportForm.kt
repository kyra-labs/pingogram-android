package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

// The export page's form (docs/DESIGN_SYSTEM.md §6 "Chat export"): format, media, messages and folder.

@Composable
internal fun ExportForm(
    content: ChatExportUiState.Content,
    onEvent: (ChatExportEvent) -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = content.form
    val title = content.chatTitle.resolve()
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    ExportColumn(modifier.verticalScroll(rememberScrollState())) {
        ExportGroup(
            rows =
                listOf(
                    ExportRow("chat") { shapes ->
                        SegmentedListItem(
                            shapes = shapes,
                            colors = exportRowColors(),
                            supportingContent = { Text(stringResource(R.string.chat_export_intro)) },
                        ) {
                            Text(title, style = LocalTextStyle.current.userContent)
                        }
                    },
                ),
        )
        FormatGroup(form.format) { onEvent(ChatExportEvent.SetFormat(it)) }
        MediaGroup(form, onEvent)
        MessagesGroup(form.sinceDay, onAll = { onEvent(ChatExportEvent.AllMessages) }, onPick = { pickingDate = true })
        FolderGroup(form, onChooseFolder)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { onEvent(ChatExportEvent.Export(title)) },
                enabled = form.canExport,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.chat_export_start), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
    if (pickingDate) {
        SinceDialog(
            initialDay = form.sinceDay,
            onDismiss = { pickingDate = false },
            onPick = { day ->
                pickingDate = false
                onEvent(ChatExportEvent.Since(day))
            },
        )
    }
}

/** HTML or JSON, as connected [ToggleButton]s with what the chosen one is for below them. */
@Composable
internal fun FormatGroup(
    format: ExportFormat,
    onSelect: (ExportFormat) -> Unit,
) {
    ExportGroup(
        title = stringResource(R.string.chat_export_section_format),
        rows =
            listOf(
                ExportRow("format") { shapes ->
                    ExportSegment(shapes) {
                        FormatButtons(format, onSelect)
                        Text(
                            text = stringResource(format.summaryRes()),
                            modifier =
                                Modifier.padding(
                                    horizontal = MaterialTheme.spacing.large,
                                    vertical = MaterialTheme.spacing.medium,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
            ),
    )
}

/**
 * The formats as a connected button group of single-select [ToggleButton]s sharing the row, on
 * `surfaceContainerHighest` while unchecked; screen readers hear radio buttons, and a choice ticks (`SegmentTick`).
 */
@Composable
private fun FormatButtons(
    format: ExportFormat,
    onSelect: (ExportFormat) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(
                start = MaterialTheme.spacing.large,
                end = MaterialTheme.spacing.large,
                top = MaterialTheme.spacing.medium,
            ),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ExportFormat.entries.forEachIndexed { index, option ->
            val checked = option == format
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(option)
                    }
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes =
                    if (index == 0) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                colors = colors,
            ) {
                val typography = MaterialTheme.typography
                Text(
                    text = stringResource(option.labelRes()),
                    maxLines = 1,
                    style = if (checked) typography.labelLargeEmphasized else typography.labelLarge,
                )
            }
        }
    }
}

/** A switch per kind of media, then the largest file to copy, which waits for a kind to be on. */
@Composable
internal fun MediaGroup(
    form: ExportForm,
    onEvent: (ChatExportEvent) -> Unit,
) {
    val rows =
        ExportMediaKind.entries.map { kind ->
            ExportRow(kind.name) { shapes ->
                ExportSwitchRow(
                    label = stringResource(kind.labelRes()),
                    checked = kind in form.media,
                    onCheckedChange = { onEvent(ChatExportEvent.SetMedia(kind, it)) },
                    shapes = shapes,
                    supporting = kind.summaryRes()?.let { stringResource(it) },
                )
            }
        } +
            ExportRow("size") { shapes ->
                SizeLimitSegment(form.sizeLimit, form.media.isNotEmpty(), shapes) {
                    onEvent(ChatExportEvent.SetSizeLimit(it))
                }
            }
    ExportGroup(
        rows = rows,
        title = stringResource(R.string.chat_export_section_media),
        footer = stringResource(R.string.chat_export_media_footer),
    )
}

/**
 * The largest file to copy: the limit in a nested list item over an expressive [Slider] through [ExportSizeLimits],
 * ticking at each step (`SegmentTick`); screen readers hear the slider by its name with the limit as its state.
 */
@Composable
internal fun SizeLimitSegment(
    step: Int,
    enabled: Boolean,
    shapes: androidx.compose.material3.ListItemShapes,
    onChange: (Int) -> Unit,
) {
    val title = stringResource(R.string.chat_export_size_limit)
    val limit = sizeLimitText(step)
    val haptics = LocalHapticFeedback.current
    ExportSegment(shapes) {
        SegmentedListItem(
            shapes = shapes,
            enabled = enabled,
            colors = nestedRowColors(),
            supportingContent = { Text(limit) },
        ) {
            Text(title)
        }
        Slider(
            value = step.toFloat(),
            onValueChange = { value ->
                val next = value.roundToInt().coerceIn(ExportSizeLimits.indices)
                if (next != step) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onChange(next)
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
                        stateDescription = limit
                    },
            enabled = enabled,
            valueRange = 0f..ExportSizeLimits.lastIndex.toFloat(),
            steps = ExportSizeLimits.size - 2,
        )
    }
}

/** All messages, or those from a day on, as radio rows; the day's row opens the date picker. */
@Composable
internal fun MessagesGroup(
    sinceDay: Long?,
    onAll: () -> Unit,
    onPick: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val since =
        sinceDay?.let { stringResource(R.string.chat_export_since_date, formatter.format(LocalDate.ofEpochDay(it))) }
    ExportGroup(
        title = stringResource(R.string.chat_export_section_messages),
        rowsModifier = Modifier.selectableGroup(),
        rows =
            listOf(
                ExportRow("all") { shapes ->
                    ExportRadioRow(stringResource(R.string.chat_export_all_messages), sinceDay == null, onAll, shapes)
                },
                ExportRow("since") { shapes ->
                    ExportRadioRow(
                        label = stringResource(R.string.chat_export_since),
                        selected = sinceDay != null,
                        onClick = onPick,
                        shapes = shapes,
                        supporting = since ?: stringResource(R.string.chat_export_since_choose),
                    )
                },
            ),
    )
}

/** The folder picked, or that none is, with a [TextButton] that picks one. */
@Composable
internal fun FolderGroup(
    form: ExportForm,
    onChooseFolder: () -> Unit,
) {
    val chosen = form.folderName != null
    val action = if (chosen) R.string.chat_export_folder_change else R.string.chat_export_folder_choose
    ExportGroup(
        title = stringResource(R.string.chat_export_section_folder),
        rows =
            listOf(
                ExportRow("folder") { shapes ->
                    val description =
                        stringResource(
                            if (chosen) {
                                R.string.chat_export_folder_change_description
                            } else {
                                R.string.chat_export_folder_choose_description
                            },
                        )
                    SegmentedListItem(
                        shapes = shapes,
                        colors = exportRowColors(),
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (form.isPickingFolder) {
                                        R.string.chat_export_folder_checking
                                    } else {
                                        R.string.chat_export_folder_summary
                                    },
                                ),
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = onChooseFolder,
                                enabled = !form.isPickingFolder,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.semantics { contentDescription = description },
                            ) {
                                Text(stringResource(action))
                            }
                        },
                    ) {
                        Text(
                            text = form.folderName ?: stringResource(R.string.chat_export_folder_none),
                            style = LocalTextStyle.current.userContent,
                        )
                    }
                },
            ),
    )
}
