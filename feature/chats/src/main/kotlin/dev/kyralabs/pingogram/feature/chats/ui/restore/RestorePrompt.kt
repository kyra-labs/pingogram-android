package dev.kyralabs.pingogram.feature.chats.ui.restore

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The restore card above the chat list ([RestorePromptViewModel], `docs/DESIGN_SYSTEM.md` §6 "Backup & restore"): it
 * opens Android's file picker for "Choose file" (`ACTION_OPEN_DOCUMENT`), Backup & restore with the backup through
 * [onOpenBackup], and grows in and shrinks away on the motion scheme's springs, as the notification request does.
 */
@Composable
internal fun RestorePromptHost(
    onOpenBackup: (document: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RestorePromptViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenBackup by rememberUpdatedState(onOpenBackup)
    val pickFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { viewModel.onEvent(RestorePromptEvent.FilePicked(it.toString())) }
        }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RestorePromptEffect.PickFile -> pickFile.launch(arrayOf("*/*"))
                is RestorePromptEffect.OpenBackup -> currentOnOpenBackup(effect.document)
            }
        }
    }
    // The card as last shown, kept while it shrinks away, so it does not change as it goes.
    var shown by remember { mutableStateOf(state) }
    if (state != RestorePromptUiState.Hidden) shown = state
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = state != RestorePromptUiState.Hidden,
        modifier = modifier,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        if (shown != RestorePromptUiState.Hidden) RestorePromptCard(shown, viewModel::onEvent)
    }
}

/**
 * "Restore from your backup of 12 Nov?" with "Not now" and "Restore", or "Restore settings from a backup?" with "Not
 * now" and "Choose file": a filled `Card` on `surfaceContainerHigh`, as the notification request.
 */
@Composable
internal fun RestorePromptCard(
    state: RestorePromptUiState,
    onEvent: (RestorePromptEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Card(
        modifier =
            modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .fillMaxWidth()
                .padding(start = spacing.small, top = spacing.small, end = spacing.small),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(start = spacing.large, top = spacing.large, end = spacing.large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_history),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title(state),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = spacing.large).semantics { heading() },
                )
            }
            Text(
                text = stringResource(R.string.restore_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.small),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
        ) {
            TextButton(onClick = { onEvent(RestorePromptEvent.NotNow) }, shapes = ButtonDefaults.shapes()) {
                Text(
                    stringResource(R.string.restore_prompt_not_now),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
            val fromFolder = state is RestorePromptUiState.FromFolder
            Button(
                onClick = { onEvent(if (fromFolder) RestorePromptEvent.Restore else RestorePromptEvent.ChooseFile) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text =
                        stringResource(
                            if (fromFolder) R.string.restore_prompt_restore else R.string.restore_prompt_choose,
                        ),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** "Restore from your backup of 12 Nov?" (day and month in the user's order) or "Restore settings from a backup?". */
@Composable
private fun title(state: RestorePromptUiState): String {
    if (state !is RestorePromptUiState.FromFolder) return stringResource(R.string.restore_prompt_title_file)
    val locale = LocalConfiguration.current.locales[0]
    val date =
        DateTimeFormatter
            .ofPattern(
                android.text.format.DateFormat
                    .getBestDateTimePattern(locale, "d MMM"),
                locale,
            ).format(state.modifiedAt.atZone(ZoneId.systemDefault()))
    return stringResource(R.string.restore_prompt_title_folder, date)
}

@PingogramPreviews
@Composable
private fun RestoreFromFolderPreview() {
    PingogramPreview {
        RestorePromptCard(
            RestorePromptUiState.FromFolder("content://tree/1", Instant.parse("2026-11-12T09:00:00Z")),
            {},
        )
    }
}

@PingogramPreviews
@Composable
private fun RestoreFromFilePreview() {
    PingogramPreview { RestorePromptCard(RestorePromptUiState.FromFile, {}) }
}
