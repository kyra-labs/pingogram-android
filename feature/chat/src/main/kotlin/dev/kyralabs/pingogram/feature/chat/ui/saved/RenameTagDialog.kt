package dev.kyralabs.pingogram.feature.chat.ui.saved

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.repository.SavedMessagesTagRepository
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Renaming a tag: a `BasicAlertDialog` in the alert dialog's shape and colors: "Rename tag" (`titleLargeEmphasized`,
 * a heading), the tag's label in a filled one-line `TextField` (at most 12 characters, user content), a
 * `LinearWavyProgressIndicator` while Telegram answers, `TextButton` "Cancel" and a default `Button` "Save".
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RenameTagDialog(
    rename: SavedTagRename,
    onEvent: (SavedTagEvent) -> Unit,
) {
    val text = rememberTextFieldState(rename.tag.label)
    val label = text.text.toString()
    BasicAlertDialog(onDismissRequest = { onEvent(SavedTagEvent.DismissRename) }) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Text(
                    stringResource(R.string.saved_tags_rename_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                TextField(
                    state = text,
                    inputTransformation = InputTransformation.maxLength(SavedMessagesTagRepository.LABEL_LENGTH_MAX),
                    label = { Text(stringResource(R.string.saved_tags_label)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = MaterialTheme.typography.bodyLarge.userContent,
                    enabled = !rename.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (rename.isSaving) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.End),
                ) {
                    TextButton(onClick = { onEvent(SavedTagEvent.DismissRename) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.saved_tags_cancel))
                    }
                    Button(
                        onClick = { onEvent(SavedTagEvent.SaveLabel(label)) },
                        enabled = !rename.isSaving && label.trim() != rename.tag.label,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(
                            stringResource(R.string.saved_tags_save),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                        )
                    }
                }
            }
        }
    }
}
