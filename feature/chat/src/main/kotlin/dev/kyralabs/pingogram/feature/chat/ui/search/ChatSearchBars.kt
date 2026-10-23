package dev.kyralabs.pingogram.feature.chat.ui.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatSearchEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatSearchPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The search field in the conversation's top bar while search is open (`docs/DESIGN_SYSTEM.md` §6, "In-chat search"):
 * the search bar's [SearchBarDefaults.InputField] in its container's pill, on the bar's `surfaceContainer`, as tall as
 * the top bar it replaces ([height]), so nothing below it moves; at most `contentMaxWidth` wide, centred, as global
 * search's. Back at its start closes search ([onClose]); a clear
 * button at its end while it holds text. The keyboard's search action closes the keyboard. Where [focusOnShow], it
 * takes the focus when it first shows, which opens the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatSearchTopBar(
    query: TextFieldState,
    height: Dp,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    focusOnShow: Boolean = true,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val spacing = MaterialTheme.spacing
    val hasText = query.text.isNotEmpty()
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(
            modifier =
                Modifier
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .fillMaxWidth()
                    .heightIn(min = height)
                    .padding(horizontal = spacing.small),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth(),
                shape = SearchBarDefaults.inputFieldShape,
                color = SearchBarDefaults.colors().containerColor,
            ) {
                SearchBarDefaults.InputField(
                    textFieldState = query,
                    searchBarState = rememberSearchBarState(),
                    onSearch = { keyboard?.hide() },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = {
                        IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.search_close),
                            )
                        }
                    },
                    trailingIcon =
                        if (hasText) {
                            {
                                IconButton(onClick = { query.clearText() }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(
                                        painter = painterResource(UiR.drawable.ic_close),
                                        contentDescription = stringResource(R.string.search_clear),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
    if (focusOnShow) LaunchedEffect(focus) { focus.requestFocus() }
}

/**
 * The bar in the composer's place while search is open (`docs/DESIGN_SYSTEM.md` §6, "In-chat search"), on the
 * composer's `surface`, as tall and as wide as the composer at one line: "Jump to date" (a calendar,
 * [SearchDateDialog]) and, in groups, "Search by sender" (the members' sheet), the counter in the middle
 * ([SearchCounter]), or the picked sender as an [InputChip] that removes them, with the counter after it; the older and
 * newer results' buttons at the end, disabled where there are none. Icon buttons have tooltips, as in toolbars.
 * [onEvent] gets the taps.
 */
@Composable
internal fun ChatSearchBottomBar(
    state: ChatSearchUiState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val results = state.results
    val found = results as? SearchResults.Found
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier =
                Modifier
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    )
                    // As wide as the composer: a bubble's width at most, centred on wide windows.
                    .fillMaxWidth()
                    .wrapContentWidth()
                    .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth)
                    .fillMaxWidth()
                    .heightIn(min = MaterialTheme.sizes.composerField)
                    .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            BarButton(R.drawable.ic_calendar_month, stringResource(R.string.search_jump_to_date)) { pickingDate = true }
            SenderAndCounter(state, onEvent)
            BarButton(R.drawable.ic_keyboard_arrow_up, stringResource(R.string.search_older), found?.hasOlder == true) {
                onEvent(ChatSearchEvent.Older)
            }
            BarButton(
                UiR.drawable.ic_keyboard_arrow_down,
                stringResource(R.string.search_newer),
                found?.hasNewer == true,
            ) {
                onEvent(ChatSearchEvent.Newer)
            }
        }
    }
    if (pickingDate) {
        SearchDateDialog(
            onDismiss = { pickingDate = false },
            onPick = { day ->
                pickingDate = false
                onEvent(ChatSearchEvent.JumpToDate(day))
            },
        )
    }
}

@PingogramPreviews
@Composable
private fun SearchTopBarPreview() {
    PingogramPreview {
        ChatSearchTopBar(TextFieldState("cats"), TopAppBarDefaults.TopAppBarExpandedHeight, onClose = {})
    }
}

@PingogramPreviews
@Composable
private fun SearchBottomBarFoundPreview() {
    PingogramPreview { ChatSearchBottomBar(ChatSearchPreviewData.found, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SearchBottomBarSenderPreview() {
    PingogramPreview { ChatSearchBottomBar(ChatSearchPreviewData.withSender, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SearchBottomBarSearchingPreview() {
    PingogramPreview { ChatSearchBottomBar(ChatSearchPreviewData.searching, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SearchBottomBarNoResultsPreview() {
    PingogramPreview { ChatSearchBottomBar(ChatSearchPreviewData.noResults, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SearchBottomBarFailedPreview() {
    PingogramPreview { ChatSearchBottomBar(ChatSearchPreviewData.failed, onEvent = {}) }
}
