package dev.kyralabs.pingogram.feature.profile.chatinfo.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.SavingIndicator
import kotlinx.coroutines.flow.drop
import java.io.File
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The sheet adding or editing a welcome message, a `ModalBottomSheet` that opens expanded: [WelcomeEditorContent].
 * Back, dragging it away and the scrim close it without saving, except while Telegram answers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WelcomeEditorSheet(
    editor: WelcomeEditor,
    onEvent: (WelcomeMessagesEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(WelcomeMessagesEvent.CloseEditor) }, sheetState = sheetState) {
        WelcomeEditorContent(editor, onEvent)
    }
}

/**
 * What the editor shows, scrolling where it does not fit:
 * - "New welcome message" / "Edit welcome message" in `titleLargeEmphasized`, a heading;
 * - the photo (`sizes.mediaMinSize`, `large` corners; a [LoadingIndicator] while it is copied) with "Change photo"
 *   (a [FilledTonalButton]) and "Remove photo" (an `error` [TextButton]), or "Add photo", which opens the photo picker;
 * - the text in a filled multi-line [TextField] labelled "Message" ("Caption" under a photo), user content, with why
 *   Telegram refused it below (a polite live region), or, for a message another app formatted, that a change of the
 *   text drops its formatting;
 * - a [LinearWavyProgressIndicator] while Telegram answers, then "Cancel" and the default [Button] "Save", disabled
 *   until there is a text or a photo.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WelcomeEditorContent(
    editor: WelcomeEditor,
    onEvent: (WelcomeMessagesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text =
                stringResource(
                    if (editor.messageId == null) R.string.welcome_new_title else R.string.welcome_edit_title,
                ),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        EditorPhotoArea(editor, onEvent)
        EditorField(editor, onEvent)
        SavingIndicator(editor.isSaving)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
        ) {
            TextButton(
                onClick = { onEvent(WelcomeMessagesEvent.CloseEditor) },
                enabled = !editor.isSaving,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.welcome_cancel), style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = { onEvent(WelcomeMessagesEvent.Save) },
                enabled = editor.canSave,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.welcome_save), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/** The photo with its buttons, or "Add photo". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorPhotoArea(
    editor: WelcomeEditor,
    onEvent: (WelcomeMessagesEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val photo = editor.photo
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        val size = MaterialTheme.sizes.mediaMinSize
        when (photo) {
            null -> {}

            EditorPhoto.Preparing -> {
                PreparingPhoto(size)
            }

            is EditorPhoto.Kept -> {
                WelcomePhotoImage(photo.photo, size)
            }

            is EditorPhoto.Picked -> {
                PickedPhoto(photo.path, size)
            }
        }
        EditorPhotoButtons(editor, onEvent)
    }
}

/** "Change photo" and "Remove photo", or "Add photo". */
@Composable
private fun EditorPhotoButtons(
    editor: WelcomeEditor,
    onEvent: (WelcomeMessagesEvent) -> Unit,
) {
    val photo = editor.photo
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        FilledTonalButton(
            onClick = { onEvent(WelcomeMessagesEvent.PickPhoto) },
            enabled = !editor.isSaving && photo != EditorPhoto.Preparing,
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_add_a_photo),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            val label = if (photo == null) R.string.welcome_add_photo else R.string.welcome_change_photo
            Text(
                text = stringResource(label),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (photo is EditorPhoto.Kept || photo is EditorPhoto.Picked) {
            TextButton(
                onClick = { onEvent(WelcomeMessagesEvent.RemovePhoto) },
                enabled = !editor.isSaving,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.welcome_remove_photo), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The photo's place while the picked photo is copied: a [LoadingIndicator] on a tonal square. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PreparingPhoto(size: Dp) {
    val description = stringResource(R.string.welcome_preparing)
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { LoadingIndicator() }
}

/** A photo just picked, from its copy on the device, at [size] with `large` corners; decorative. */
@Composable
private fun PickedPhoto(
    path: String,
    size: Dp,
) {
    val context = LocalPlatformContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val request =
        remember(context, path, sizePx) {
            ImageRequest
                .Builder(context)
                .data(File(path))
                .size(sizePx)
                .build()
        }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier =
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
        colorFilter = MaterialTheme.pingogramColors.mediaColorFilter,
    )
}

/** The message's text, with why Telegram refused it, or that a change drops another app's formatting. */
@Composable
private fun EditorField(
    editor: WelcomeEditor,
    onEvent: (WelcomeMessagesEvent) -> Unit,
) {
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val error = editor.error?.let { errorMessage(resources, it, showDetails) }
    val text = rememberTextFieldState(editor.text)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(text) {
        snapshotFlow { text.text.toString() }.drop(1).collect { currentOnEvent(WelcomeMessagesEvent.TextChanged(it)) }
    }
    val hasPhoto = editor.photo != null
    TextField(
        state = text,
        modifier = Modifier.fillMaxWidth(),
        enabled = !editor.isSaving,
        textStyle = MaterialTheme.typography.bodyLarge.userContent,
        label = {
            Text(stringResource(if (hasPhoto) R.string.welcome_caption_label else R.string.welcome_text_label))
        },
        supportingText =
            when {
                error != null -> {
                    { Text(error, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                }

                editor.isFormatted -> {
                    { Text(stringResource(R.string.welcome_plain_text)) }
                }

                else -> {
                    null
                }
            },
        isError = error != null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = MIN_LINES, maxHeightInLines = MAX_LINES),
    )
}

private const val MIN_LINES = 3
private const val MAX_LINES = 8
