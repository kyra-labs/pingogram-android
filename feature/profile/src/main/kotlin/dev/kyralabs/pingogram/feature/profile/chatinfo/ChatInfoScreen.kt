package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.plusBottom
import dev.kyralabs.pingogram.core.ui.component.topOnly
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSectionHeader
import dev.kyralabs.pingogram.feature.profile.ui.ProfileTopBar
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaActions
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaEvent
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaLoadMoreEffect
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaPane
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaUiState
import dev.kyralabs.pingogram.feature.profile.ui.SupportingPanes
import dev.kyralabs.pingogram.feature.profile.ui.profilePageColor
import dev.kyralabs.pingogram.feature.profile.ui.sharedMediaItems
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The info page of a group or channel (`REQUIREMENTS.md` FR-P3, FR-P4; `docs/DESIGN_SYSTEM.md` §6 "Group and channel
 * info"), laid out as the user's profile:
 *
 * - **Compact and medium windows**: one page under the profile's large flexible top bar ([ProfileTopBar]: the photo,
 *   the title and "Public group · 1,024 members" collapsing as it scrolls), then the actions ([ChatInfoActionsRow]),
 *   the description and link ([ChatInfoGroup]), the first members ([ChatMembersGroup]), administration for those
 *   whose rights allow it ([ChatAdministrationGroup]), the rest ([ChatInfoMoreGroup]), and the shared media, all in one
 *   [LazyVerticalGrid].
 * - **Expanded and wider**: the page as the main pane and the shared media as the supporting pane beside it
 *   ([SupportingPanes]), as the profile does.
 *
 * On the profile's page color, its rows segmented list items; inset by the window margin and capped at the content's
 * width. While the chat is looked up a delayed loading indicator shows; a chat that cannot be read shows why, with "Try
 * again". [onBack] is the top bar's back button.
 */
@Suppress("LongParameterList") // The states, where their events go, and the app's navigation.
@Composable
internal fun ChatInfoScreen(
    state: ChatInfoUiState,
    media: SharedMediaUiState,
    onEvent: (ChatInfoEvent) -> Unit,
    onMediaEvent: (SharedMediaEvent) -> Unit,
    onBack: () -> Unit,
    mediaActions: SharedMediaActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    twoPanes: Boolean = rememberTwoPanes(),
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val page = ChatInfoPageParts(state, media, onEvent, onMediaEvent, mediaActions)
    if (twoPanes) {
        SupportingPanes(
            main = {
                ChatInfoPage(
                    page,
                    onBack,
                    snackbarHostState,
                    Modifier,
                    rememberLazyGridState(),
                    withMedia = false,
                )
            },
            supporting = {
                SharedMediaPane(
                    media,
                    onMediaEvent,
                    mediaActions,
                    gridState,
                    emptyBody = R.string.chat_info_media_empty_body,
                )
            },
            modifier = modifier,
        )
    } else {
        ChatInfoPage(page, onBack, snackbarHostState, modifier, gridState, withMedia = true)
    }
}

/** What the page shows and where its events go, handed down as one. */
internal class ChatInfoPageParts(
    val state: ChatInfoUiState,
    val media: SharedMediaUiState,
    val onEvent: (ChatInfoEvent) -> Unit,
    val onMediaEvent: (SharedMediaEvent) -> Unit,
    val mediaActions: SharedMediaActions,
)

/** Whether the window has room for the supporting pane beside the page: expanded and wider. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun rememberTwoPanes(): Boolean =
    calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).maxHorizontalPartitions > 1

/** The page: its top bar collapsing with the grid's scroll, the snackbars, and the body ([ChatInfoBody]). */
@Suppress("LongParameterList") // The page's parts, back, and the states it shares with the other pane.
@Composable
private fun ChatInfoPage(
    page: ChatInfoPageParts,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
    gridState: LazyGridState,
    withMedia: Boolean,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = profilePageColor(),
        topBar = { ProfileTopBar((page.state as? ChatInfoUiState.Content)?.header, scrollBehavior, onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ChatInfoBody(page, innerPadding, gridState, withMedia)
    }
}

/**
 * Below the top bar: the loading indicator, the error, or the page's grid. Its own composable, so the frames of a
 * collapsing top bar stop here (`docs/PERFORMANCE.md` §4).
 */
@Composable
private fun ChatInfoBody(
    page: ChatInfoPageParts,
    innerPadding: PaddingValues,
    gridState: LazyGridState,
    withMedia: Boolean,
) {
    val modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding)
    when (val state = page.state) {
        ChatInfoUiState.Loading -> {
            val description = stringResource(R.string.chat_info_loading)
            Box(modifier.padding(innerPadding), contentAlignment = Alignment.Center) {
                DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
            }
        }

        is ChatInfoUiState.Error -> {
            ChatInfoError(state.error, onRetry = { page.onEvent(ChatInfoEvent.Retry) }, modifier.padding(innerPadding))
        }

        is ChatInfoUiState.Content -> {
            ChatInfoGrid(state, page, innerPadding, gridState, withMedia, modifier)
        }
    }
}

/** The page's lazy grid: full-width items for the actions and rows, then, [withMedia], the shared media. */
@Suppress("LongParameterList")
@Composable
private fun ChatInfoGrid(
    state: ChatInfoUiState.Content,
    page: ChatInfoPageParts,
    innerPadding: PaddingValues,
    gridState: LazyGridState,
    withMedia: Boolean,
    modifier: Modifier,
) {
    val margin = windowMargin()
    val sizes = MaterialTheme.sizes
    val spacing = MaterialTheme.spacing
    if (withMedia) SharedMediaLoadMoreEffect(gridState, page.media, page.onMediaEvent)
    BoxWithConstraints(modifier) {
        val inset = max(margin, (maxWidth - sizes.contentMaxWidth) / 2)
        val columns = (maxWidth / sizes.sharedMediaCell).toInt().coerceAtLeast(1)
        val cellSizePx = with(LocalDensity.current) { (maxWidth / columns).roundToPx() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(remember(innerPadding) { innerPadding.topOnly() })
                    .testTag(CHAT_INFO_GRID_TEST_TAG),
            state = gridState,
            contentPadding =
                remember(innerPadding, spacing) { innerPadding.withTop(0.dp).plusBottom(spacing.extraLarge) },
        ) {
            chatInfoItems(state, page.onEvent, inset)
            if (withMedia && page.media.chatId != null) {
                fullWidth(key = MEDIA_HEADER, inset = inset, gapAbove = true) {
                    ProfileSectionHeader(stringResource(R.string.profile_section_media))
                }
                sharedMediaItems(
                    page.media,
                    page.onMediaEvent,
                    page.mediaActions,
                    cellSizePx,
                    inset,
                    emptyBody = R.string.chat_info_media_empty_body,
                )
            }
        }
    }
}

/** The actions and rows of the page, each a full-width item inset by [inset], `spacing.extraLarge` apart. */
private fun LazyGridScope.chatInfoItems(
    state: ChatInfoUiState.Content,
    onEvent: (ChatInfoEvent) -> Unit,
    inset: Dp,
) {
    fullWidth(key = ACTIONS, inset = inset) {
        ChatInfoActionsRow(state.actions, onEvent, Modifier.padding(top = MaterialTheme.spacing.small))
    }
    if (state.description != null || state.link != null) {
        fullWidth(key = INFO, inset = inset, gapAbove = true) {
            ChatInfoGroup(state.description, state.link, onEvent)
        }
    }
    state.members?.let { members ->
        fullWidth(key = MEMBERS, inset = inset, gapAbove = true) { ChatMembersGroup(members, state.kind, onEvent) }
    }
    state.administration?.let { administration ->
        fullWidth(key = ADMINISTRATION, inset = inset, gapAbove = true) {
            ChatAdministrationGroup(administration, onEvent)
        }
    }
    if (state.actions.canClearHistory || state.actions.canDelete) {
        fullWidth(key = MORE, inset = inset, gapAbove = true) { ChatInfoMoreGroup(state.actions, state.kind, onEvent) }
    }
}

/**
 * A full-width item of the grid, inset by [inset] at its sides, `spacing.extraLarge` below the one before [gapAbove].
 */
private fun LazyGridScope.fullWidth(
    key: String,
    inset: Dp,
    gapAbove: Boolean = false,
    content: @Composable () -> Unit,
) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) {
        val gap = if (gapAbove) MaterialTheme.spacing.extraLarge else 0.dp
        Box(Modifier.fillMaxWidth().padding(start = inset, end = inset, top = gap)) { content() }
    }
}

/** Why the page cannot show: an [EmptyState] on `Cookie4Sided` in `errorContainer`, with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatInfoError(
    error: DomainError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.chat_info_error_title),
        body =
            if (error == DomainError.NotFound) {
                stringResource(R.string.chat_info_not_found_body)
            } else {
                errorText(error)
            },
        modifier = modifier,
        containerColor = colors.errorContainer,
        iconColor = colors.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.profile_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

/** The test tag of the page's grid, for tests that scroll it. */
internal const val CHAT_INFO_GRID_TEST_TAG = "chat_info_grid"

private const val ACTIONS = "actions"
private const val INFO = "info"
private const val MEMBERS = "members"
private const val ADMINISTRATION = "administration"
private const val MORE = "more"
private const val MEDIA_HEADER = "media-header"
