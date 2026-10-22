package dev.kyralabs.pingogram.feature.chat.ui.bot

import android.view.KeyEvent.FLAG_SOFT_KEYBOARD
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.BotInput
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotPreviewData

/**
 * The commands suggested while one is typed (`docs/DESIGN_SYSTEM.md` §6 "Bot command suggestions"), above the composer
 * like Google Messages' suggestions: one group of segmented list items ([SegmentedListItem], `SegmentedGap` apart,
 * `large` outer corners) on `surfaceContainer`, the pill's color, inset `spacing.small` from the sides as the pill is,
 * at most `sizes.inputPanelHeight` (about four rows) or a third of the window tall, scrolling beyond. Each row shows
 * the command with the typed part highlighted ([CommandText]), what it does on one line, in a group its bot's avatar,
 * and the insert button: [bots]' suggestions, none while not [enabled] (recording). A tap sends the command
 * ([BotInput.onSendCommand]); the insert button puts it into the field ([BotInput.onInsertCommand]).
 *
 * It grows in from the bottom on the motion scheme's default spatial spring with an effects-spring fade, and shrinks
 * away as the command's arguments start; the last suggestions stay while it leaves. A hardware keyboard picks among
 * them with the arrow keys, sends with Enter and inserts with Tab, Escape hides them ([CommandSuggestionsState]); the
 * row picked so shows selected (`secondaryContainer`, `large` all round). Screen readers hear "3 commands" as the
 * pane's title as it shows, and each row as one item ([commandSemantics]).
 */
@Composable
internal fun CommandSuggestionsArea(
    bots: BotInput,
    state: CommandSuggestionsState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val suggestions = bots.suggestions.takeIf { enabled }
    val onSend = bots.onSendCommand
    val onInsert = bots.onInsertCommand
    val motionScheme = MaterialTheme.motionScheme
    var shown by remember { mutableStateOf(suggestions) }
    val visible = state.isShown(suggestions)
    if (visible) shown = suggestions
    LaunchedEffect(suggestions?.query) { state.onQuery(suggestions?.query) }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            expandVertically(motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Bottom) +
                fadeIn(motionScheme.defaultEffectsSpec()),
        exit =
            shrinkVertically(motionScheme.fastSpatialSpec(), shrinkTowards = Alignment.Bottom) +
                fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        shown?.let { CommandSuggestionList(it, state.highlighted, onSend, onInsert) }
    }
}

/** The suggested commands, the one at [highlighted] selected while a hardware keyboard picks (-1: none). */
@Composable
internal fun CommandSuggestionList(
    suggestions: CommandSuggestions,
    highlighted: Int,
    onSend: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val commands = suggestions.commands
    val listState = rememberLazyListState()
    LaunchedEffect(highlighted) { if (highlighted >= 0) listState.animateScrollToItem(highlighted) }
    val title = pluralStringResource(R.plurals.bot_command_suggestions, commands.size, commands.size)
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSuggestionsHeight())
                .padding(start = spacing.small, top = spacing.small, end = spacing.small)
                .semantics { paneTitle = title }
                .testTag(SUGGESTIONS_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(commands, key = { _, item -> item.text }, contentType = { _, _ -> CONTENT_TYPE_COMMAND }) {
            index,
            item,
            ->
            val insert = { onInsert(item.text) }
            SegmentedListItem(
                selected = index == highlighted,
                onClick = { onSend(item.text) },
                shapes = ListItemDefaults.segmentedShapes(index, commands.size),
                modifier = Modifier.commandSemantics(item, insert),
                colors = colors,
                leadingContent = item.bot?.let { bot -> { CommandBotAvatar(bot, colors.containerColor) } },
                trailingContent = { InsertCommandButton(insert) },
                supportingContent =
                    if (item.description.isNotBlank()) {
                        { CommandDescription(item, maxLines = 1) }
                    } else {
                        null
                    },
                content = { CommandText(item, matched = suggestions.query.length) },
            )
        }
    }
}

/** The tallest the suggestions get: about four rows (the emoji panel's height), at most a third of the window. */
@Composable
private fun maxSuggestionsHeight(): Dp {
    val windowHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    return min(MaterialTheme.sizes.inputPanelHeight, windowHeight / WINDOW_FRACTION)
}

/**
 * What a hardware keyboard does with the suggestions while the field has the focus ([onKeyEvent], from the field's
 * key events): the up and down arrows pick one ([highlighted]), Enter sends it, Tab inserts it, Escape hides them
 * until the command typed changes. Enter of the on-screen keyboard keeps starting a line.
 */
@Stable
internal class CommandSuggestionsState {
    /** The suggestion the arrow keys picked; -1 before they picked one, when Enter and Tab take the first. */
    var highlighted by mutableIntStateOf(NONE)
        private set

    /** The command typed when Escape hid the suggestions; null while they are not hidden. */
    private var hiddenQuery by mutableStateOf<String?>(null)

    /** Whether [suggestions] show: there are some, and Escape did not hide them for this command. */
    fun isShown(suggestions: CommandSuggestions?): Boolean = suggestions != null && suggestions.query != hiddenQuery

    /** The command typed changed to [query]: the arrow keys start again at the top, and hidden ones show again. */
    fun onQuery(query: String?) {
        highlighted = NONE
        if (query != hiddenQuery) hiddenQuery = null
    }

    /** What the composer's field does first with its keys, for [bots]' suggestions ([onKeyEvent]). */
    fun keysFor(bots: BotInput): (KeyEvent) -> Boolean =
        { event -> onKeyEvent(event, bots.suggestions, bots.onSendCommand, bots.onInsertCommand) }

    /**
     * Handles [event] for the [suggestions] shown: true if it picked, sent ([onSend]), inserted ([onInsert]) or hid
     * them, so the field does not act on it too.
     */
    fun onKeyEvent(
        event: KeyEvent,
        suggestions: CommandSuggestions?,
        onSend: (String) -> Unit,
        onInsert: (String) -> Unit,
    ): Boolean {
        if (event.type != KeyEventType.KeyDown || !isShown(suggestions) || suggestions == null) return false
        val commands = suggestions.commands
        val current = commands[highlighted.coerceIn(0, commands.lastIndex)]
        return when {
            event.key == Key.DirectionDown -> {
                highlighted = (highlighted + 1).coerceAtMost(commands.lastIndex)
                true
            }

            event.key == Key.DirectionUp -> {
                highlighted = (highlighted - 1).coerceAtLeast(0)
                true
            }

            event.isHardwareEnter() -> {
                onSend(current.text)
                true
            }

            event.key == Key.Tab && !event.isShiftPressed -> {
                onInsert(current.text)
                true
            }

            event.key == Key.Escape -> {
                hiddenQuery = suggestions.query
                true
            }

            else -> {
                false
            }
        }
    }

    private companion object {
        const val NONE = -1
    }
}

/** Enter on a hardware keyboard without a modifier; the on-screen keyboard's Enter is flagged as its own. */
private fun KeyEvent.isHardwareEnter(): Boolean =
    (key == Key.Enter || key == Key.NumPadEnter) &&
        !isShiftPressed &&
        !isCtrlPressed &&
        !isAltPressed &&
        !isMetaPressed &&
        nativeKeyEvent.flags and FLAG_SOFT_KEYBOARD == 0

/** The suggestions take at most this part of the window's height. */
private const val WINDOW_FRACTION = 3

private const val CONTENT_TYPE_COMMAND = "command"

/** The suggestions, for UI tests. */
internal const val SUGGESTIONS_TEST_TAG = "command_suggestions"

@PingogramPreviews
@Composable
private fun CommandSuggestionsPreview() {
    PingogramPreview { CommandSuggestionList(BotPreviewData.suggestions, highlighted = -1, onSend = {}, onInsert = {}) }
}

@PingogramPreviews
@Composable
private fun CommandSuggestionsGroupPreview() {
    PingogramPreview {
        CommandSuggestionList(
            BotPreviewData.groupSuggestions,
            highlighted = 1,
            onSend = {},
            onInsert = {},
        )
    }
}
