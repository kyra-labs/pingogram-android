package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.delay
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The Chats tab's top bar with the folder tabs ([ChatListTopBar]), or the archive's ([ArchiveTopBar]). */
@Composable
internal fun ChatListScreenTopBar(
    state: ChatListUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    onEvent: (ChatListEvent) -> Unit,
    onBack: () -> Unit,
) {
    val clearSelection = { onEvent(ChatListEvent.ClearSelection) }
    when (state.kind) {
        ChatListKind.Chats -> {
            ChatListTopBar(
                state = state,
                scrollBehavior = scrollBehavior,
                onSelectFolder = { onEvent(ChatListEvent.SelectFolder(it)) },
                onClearSelection = clearSelection,
            )
        }

        ChatListKind.Archive -> {
            ArchiveTopBar(state.selection?.chatIds?.size, scrollBehavior, onBack, clearSelection)
        }
    }
}

/**
 * The top bar of the Chats tab, the connection pill and the folder tabs below it, on the screen's `surfaceContainer`
 * above the list's sheet (`docs/DESIGN_SYSTEM.md` §2.2, §6): a small, pinned [TopAppBar] (56 dp,
 * `sizes.denseTopAppBar`) with `titleLargeEmphasized`, as dense lists get, so the first chat starts high on the
 * screen (§7). The connection pill ([ConnectionPill]) sits below the bar. The title, the pill and the tabs keep the
 * window margin ([windowMargin]): on medium and wider windows they line up with the rows' content.
 *
 * In selection mode the same bar shows how many chats are selected, with a button that clears the selection, and the
 * tabs stay but are disabled: nothing below moves, the tabs still say which list the selection is in, and switching
 * lists, which would drop the selection, waits until the selection is done.
 */
@Composable
internal fun ChatListTopBar(
    state: ChatListUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    onSelectFolder: (Int?) -> Unit,
    onClearSelection: () -> Unit,
) {
    val selectedCount = state.selection?.chatIds?.size
    // The window margin (§7): 16 dp on compact windows, 24 dp on medium and wider ones.
    val margin = windowMargin()
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // Behind the bar: what the user touches there stays the bar's.
        ConnectedAnnouncement(state.connection, Modifier.matchParentSize())
        // As wide as the rows below, so the title and the tabs line up with them on wide windows.
        Column(Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth)) {
            TopAppBar(
                // The bar insets its title by the compact margin itself.
                modifier = Modifier.padding(horizontal = margin - MaterialTheme.spacing.windowMarginCompact),
                title = {
                    SelectionTitle(
                        title = stringResource(R.string.chat_list_title),
                        selectedCount = selectedCount,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                navigationIcon = { if (selectedCount != null) ClearSelectionButton(onClearSelection) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                // A dense list's bar (§7): 56 dp rather than Material's 64 dp.
                expandedHeight = MaterialTheme.sizes.denseTopAppBar,
                scrollBehavior = scrollBehavior,
            )
            // In line with the title and with the tabs.
            ConnectionPill(connection = state.connection, modifier = Modifier.padding(horizontal = margin))
            if (state.folderTabs.isNotEmpty()) {
                FolderTabs(
                    tabs = state.folderTabs,
                    selectedFolderId = state.selectedFolderId,
                    onSelect = onSelectFolder,
                    enabled = selectedCount == null,
                    margin = margin,
                )
            }
        }
    }
}

/**
 * The archive's top bar (`REQUIREMENTS.md` FR-L9): a small [TopAppBar] with back and "Archived chats", on the screen's
 * `surfaceContainer` above the list's sheet. In selection mode it shows how many chats are selected, with a button
 * that clears the selection in place of back.
 */
@Composable
internal fun ArchiveTopBar(
    selectedCount: Int?,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onClearSelection: () -> Unit,
) {
    TopAppBar(
        title = {
            SelectionTitle(
                title = stringResource(R.string.chat_list_archived_chats),
                selectedCount = selectedCount,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        },
        navigationIcon = {
            if (selectedCount != null) {
                ClearSelectionButton(onClearSelection)
            } else {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.chat_list_archive_back),
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        expandedHeight = MaterialTheme.sizes.denseTopAppBar,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * [title], or "3 selected" while chats are selected, cross-fading on the motion scheme's effects springs when selection
 * mode starts or ends. Screen readers hear the count change.
 */
@Composable
private fun SelectionTitle(
    title: String,
    selectedCount: Int?,
    style: TextStyle,
) {
    val motionScheme = MaterialTheme.motionScheme
    val resources = LocalResources.current
    AnimatedContent(
        targetState = selectedCount,
        transitionSpec = {
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                fadeOut(motionScheme.fastEffectsSpec())
        },
        label = "title",
        contentKey = { it != null },
    ) { count ->
        Text(
            text =
                if (count !=
                    null
                ) {
                    resources.getQuantityString(R.plurals.chat_list_selected, count, count)
                } else {
                    title
                },
            modifier =
                Modifier.semantics {
                    heading()
                    if (count != null) liveRegion = LiveRegionMode.Polite
                },
            maxLines = 1,
            style = style,
        )
    }
}

/** Leaves selection mode, in the top bar's navigation slot. */
@Composable
private fun ClearSelectionButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(UiR.drawable.ic_close),
            contentDescription = stringResource(R.string.chat_list_clear_selection),
        )
    }
}

/**
 * "Connecting…" and the like while not connected (`REQUIREMENTS.md` FR-L5): a pill in `surfaceContainerHighest` with
 * an expressive loading indicator, below the top bar. It grows in and out on the motion scheme's springs, keeping its
 * last text while it leaves, and screen readers announce each change. Connected, it takes no space.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectionPill(
    connection: ConnectionStatus,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = connection.takeIf { it != ConnectionStatus.Ready },
        modifier = modifier,
        transitionSpec = {
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec()) using
                SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() }
        },
        label = "connectionPill",
    ) { status ->
        if (status != null) {
            Surface(
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = MaterialTheme.spacing.medium,
                            vertical = MaterialTheme.spacing.extraSmall,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(Modifier.size(MaterialTheme.sizes.inlineProgressIndicator))
                    Text(
                        text = stringResource(status.labelRes()),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * "Connected" for screen readers once the connection is back after the pill showed a state (`docs/DESIGN_SYSTEM.md`
 * §9): a polite live region behind the bar ([modifier] sizes it: a node without bounds would not reach them), empty but
 * for the [CONNECTED_ANNOUNCEMENT_MILLIS] it holds the word, which change Android announces. Nothing is drawn, and
 * nothing is announced while the connection was ready all along, as at a start that finds it connected.
 */
@Composable
private fun ConnectedAnnouncement(
    connection: ConnectionStatus,
    modifier: Modifier = Modifier,
) {
    val ready = connection == ConnectionStatus.Ready
    // Read only by the effect, so it composes nothing again.
    var wasDisconnected by remember { mutableStateOf(false) }
    var announcing by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (!ready) {
            wasDisconnected = true
            announcing = false
        } else if (wasDisconnected) {
            wasDisconnected = false
            announcing = true
            delay(CONNECTED_ANNOUNCEMENT_MILLIS)
            announcing = false
        }
    }
    val connected = stringResource(R.string.chat_list_connected)
    Box(
        modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            if (announcing) contentDescription = connected
        },
    )
}

/** How long [ConnectedAnnouncement] holds "Connected": long enough to be read, short enough not to be found later. */
internal const val CONNECTED_ANNOUNCEMENT_MILLIS = 3_000L

private fun ConnectionStatus.labelRes(): Int =
    when (this) {
        ConnectionStatus.WaitingForNetwork -> R.string.chat_list_waiting_for_network
        ConnectionStatus.ConnectingToProxy -> R.string.chat_list_connecting_to_proxy
        ConnectionStatus.Connecting -> R.string.chat_list_connecting
        ConnectionStatus.Updating, ConnectionStatus.Ready -> R.string.chat_list_updating
    }
