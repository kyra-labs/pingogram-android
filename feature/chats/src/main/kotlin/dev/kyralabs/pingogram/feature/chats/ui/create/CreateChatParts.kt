package dev.kyralabs.pingogram.feature.chats.ui.create

import android.content.Context
import android.content.Intent
import android.icu.text.ListFormatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of the screens that create a group or a channel (docs/DESIGN_SYSTEM.md §6, "New group" and "New channel"):
// a small top bar on surfaceContainer above a rounded sheet, the photo, Material's rounded tonal text fields, the
// form's end and the dialog about the people Telegram did not add.

/**
 * A step of creating a group or channel, pushed above the New chat screen as it is: a small [TopAppBar] with back
 * ([onBack], none where there is nothing to go back to) and [title] in `titleLargeEmphasized` on `surfaceContainer`,
 * and [content] on a rounded `surface` sheet ([ContentSheet]) below it, clear of the keyboard.
 */
@Composable
internal fun CreateChatScaffold(
    title: String,
    onBack: (() -> Unit)?,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        // The top bar's container; the content sits on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { CreateChatTopBar(title, onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            content(contentPadding)
        }
    }
}

@Composable
private fun CreateChatTopBar(
    title: String,
    onBack: (() -> Unit)?,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                maxLines = 1,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.new_chat_back),
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
    )
}

/**
 * A form on the sheet: a column that scrolls, at most `contentMaxWidth` wide and centered, inside the window margin,
 * its parts [MaterialTheme.spacing] `medium` apart.
 */
@Composable
internal fun CreateChatForm(
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = windowMargin(), vertical = MaterialTheme.spacing.large)
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            content = content,
        )
    }
}

/**
 * A field of a creation form in Material's rounded tonal style (`TextFieldDefaults.roundedShape`, `tonalColors()`),
 * directly on the sheet, as the contact forms have it (`docs/DESIGN_SYSTEM.md` §6, "Text fields"), with [error] (null
 * without one) or [supporting] below it; an error is announced politely to screen readers.
 */
@Suppress("LongParameterList") // A text field's options; all but the value, its change and its label are optional.
@Composable
internal fun CreateChatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onImeAction: () -> Unit = {},
    prefix: String? = null,
    textStyle: TextStyle? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
        label = { Text(label) },
        prefix = prefix?.let { text -> { Text(text) } },
        supportingText =
            (error ?: supporting)?.let { text ->
                {
                    val announced = error != null
                    Text(text, Modifier.semantics { if (announced) liveRegion = LiveRegionMode.Polite })
                }
            },
        isError = error != null,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions { onImeAction() },
        singleLine = singleLine,
        minLines = 1,
        maxLines = if (singleLine) 1 else DESCRIPTION_MAX_LINES,
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/**
 * The end of a creation form: the `FLOOD_WAIT` countdown ([floodWait]) and the progress of a request ([isWorking]),
 * then the buttons ([actions]) at the end, wrapping onto more lines when they do not fit side by side.
 */
@Composable
internal fun FormEnd(
    floodWait: FloodWait?,
    isWorking: Boolean,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.End,
    actions: @Composable () -> Unit,
) {
    floodWait?.let { FloodWaitNote(it) }
    if (isWorking) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) { actions() }
}

/**
 * While Telegram asks to wait (`FLOOD_WAIT`): the time left in the error role, and beside it a determinate
 * [CircularWavyProgressIndicator] that runs down (`docs/DESIGN_SYSTEM.md` §5), gliding from one second to the next on
 * the effects spring. The indicator is hidden from screen readers; they hear the text as a polite live region, under a
 * minute in 15-second steps rather than every second.
 */
@Composable
internal fun FloodWaitNote(
    floodWait: FloodWait,
    modifier: Modifier = Modifier,
) {
    val seconds = floodWait.seconds
    val fraction by animateFloatAsState(
        targetValue = if (floodWait.totalSeconds > 0) seconds.toFloat() / floodWait.totalSeconds else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "FloodWait",
    )
    val text = stringResource(R.string.create_flood_wait, durationText(seconds))
    val announced = if (seconds in 1 until SECONDS_PER_MINUTE) (seconds + STEP - 1) / STEP * STEP else seconds
    val announcement = stringResource(R.string.create_flood_wait, durationText(announced))
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularWavyProgressIndicator(
            progress = { fraction },
            modifier = Modifier.clearAndSetSemantics {},
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = text,
            modifier =
                Modifier.clearAndSetSemantics {
                    contentDescription = announcement
                    liveRegion = LiveRegionMode.Polite
                },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * The people Telegram did not add to the new group or channel ([isChannel]): how many, their names where known, and
 * why it usually happens; "Share invite link" sends them the link instead, through the share sheet, "OK" goes on to
 * the chat ([NotAddedEvent]). Not destructive, but it reports what happened before the chat opens, so it is a dialog
 * rather than a snackbar the chat would hide.
 */
@Composable
internal fun NotAddedAlert(
    dialog: NotAddedDialog,
    isChannel: Boolean,
    onEvent: (NotAddedEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(NotAddedEvent.Dismiss) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(NotAddedEvent.ShareLink) },
                enabled = !dialog.isSharing,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.create_not_added_share), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(NotAddedEvent.Dismiss) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.create_not_added_ok), style = MaterialTheme.typography.labelLarge)
            }
        },
        title = {
            Text(pluralStringResource(R.plurals.create_not_added_title, dialog.count, dialog.count))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                if (dialog.names.isNotEmpty()) {
                    // In the language's list style ("Ada, Grace, and Alan"), each name isolated from the others.
                    val locale = LocalConfiguration.current.locales[0]
                    val names =
                        remember(dialog.names, locale) {
                            ListFormatter
                                .getInstance(
                                    locale,
                                ).format(dialog.names.map { it.bidiIsolated() })
                                .bidiIsolated()
                        }
                    Text(names)
                }
                Text(
                    stringResource(
                        if (isChannel) R.string.create_not_added_text_channel else R.string.create_not_added_text,
                    ),
                )
                if (dialog.isSharing) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
        },
    )
}

/** Opens Android's share sheet with [text], e.g. an invite link. */
internal fun shareText(
    context: Context,
    text: String,
) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/** How many lines the description field grows to before it scrolls. */
private const val DESCRIPTION_MAX_LINES = 5

private const val SECONDS_PER_MINUTE = 60L

/** Rounding up never claims less time than is left. */
private const val STEP = 15L
