package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.isLargeFontScale
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedPreviewStates
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Channel feed (`REQUIREMENTS.md` FR-X5, `docs/DESIGN_SYSTEM.md` §6 "Channel feed"), pushed above the Chats tab: a
 * small [TopAppBar] with back ([onBack]), "Feed" and how many posts are unread, and below it on a rounded `surface`
 * sheet ([ContentSheet]) the new posts as cards ([FeedPostCard]), then "Earlier · read" (or, once all are read, "You're
 * all caught up") and the read ones. An unread card 60% on screen for a second is reported seen ([FeedEvent.Seen]).
 * Search swaps the top bar for a search field and the cards for the posts found in the channels ([search],
 * [FeedSearchResults]), which open as a card does; back closes it.
 */
@Composable
internal fun FeedScreen(
    state: FeedUiState,
    onEvent: (FeedEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
    openPostKey: String? = null,
    isRefreshing: Boolean = false,
    reactionChoice: FeedReactionChoice? = null,
    search: FeedSearchUiState = FeedSearchUiState(),
    onSearchEvent: (FeedSearchEvent) -> Unit = {},
) {
    var filtering by rememberSaveable { mutableStateOf(false) }
    if (filtering) {
        FeedFilterSheet(state.channels, state.filter, onEvent, onDismiss = { filtering = false })
    }
    state.adSheet?.let { sheet -> FeedAdSheet(sheet, onEvent) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val query = rememberTextFieldState()
    val closeSearch = {
        query.clearText()
        searching = false
    }
    FeedSearchEffects(searching, query, state.filter.chosen, onSearchEvent, onClose = closeSearch)
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbars) },
        // The sheet paints the top bar's container around itself (ContentSheet), so nothing lies under it; the content
        // keeps the color it had on that container.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            if (searching) {
                FeedSearchBar(query, onClose = closeSearch)
            } else {
                FeedTopBar(state, onBack, onSearch = { searching = true }, onFilter = { filtering = true })
            }
        },
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            if (searching) {
                FeedSearchResults(
                    search,
                    onSearchEvent,
                    { chatId, id -> onEvent(FeedEvent.Open(chatId, id)) },
                    contentPadding,
                )
            } else {
                FeedBody(state, onEvent, onBack, contentPadding, openPostKey, isRefreshing, reactionChoice)
            }
        }
    }
}

/** The feed's cards, or while there are none, the skeleton, "No new posts" or why they failed to load. */
@Composable
private fun FeedBody(
    state: FeedUiState,
    onEvent: (FeedEvent) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    openPostKey: String? = null,
    isRefreshing: Boolean = false,
    reactionChoice: FeedReactionChoice? = null,
) {
    val body = Modifier.fillMaxSize().padding(contentPadding)
    when (val content = state.content) {
        FeedContent.Loading -> {
            FeedSkeleton(contentPadding)
        }

        FeedContent.NoNewPosts -> {
            val error = state.error
            if (error == null) NoNewPosts(onEvent, onBack, body) else FeedFailed(error, onEvent, body)
        }

        is FeedContent.Posts -> {
            FeedList(
                content,
                state.error,
                state.ads,
                onEvent,
                contentPadding,
                openPostKey,
                isRefreshing,
                reactionChoice,
            )
        }
    }
}

/**
 * Back, then "Feed" in `titleLargeEmphasized` over "8 unread · 14 channels" ("8 unread" at large font scales), then
 * search ([onSearch]) and the filter ([onFilter]).
 */
@Composable
private fun FeedTopBar(
    state: FeedUiState,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = stringResource(R.string.feed_title),
                    modifier = Modifier.semantics { heading() },
                    maxLines = 1,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Text(
                    text = feedSummary(state),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(R.string.feed_back))
            }
        },
        actions = {
            IconButton(onClick = onSearch, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_search), stringResource(R.string.feed_search))
            }
            // The board's tonal square: the filter on a tonal button with medium corners.
            FilledTonalIconButton(onClick = onFilter, shape = MaterialTheme.shapes.medium) {
                Icon(painterResource(UiR.drawable.ic_filter_list), stringResource(R.string.feed_filter))
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        // The filter's 40 dp container ends on the window margin, as back's arrow starts on it (docs/DESIGN_SYSTEM.md
        // §7): the bar keeps spacing.extraSmall, the button's touch target another.
        contentPadding = PaddingValues(end = MaterialTheme.spacing.small),
    )
}

/** "Loading posts…", or "8 unread · 14 channels", without the channels at large font scales. */
@Composable
private fun feedSummary(state: FeedUiState): String {
    if (state.content == FeedContent.Loading) return stringResource(R.string.feed_loading)
    val unread = pluralStringResource(R.plurals.feed_unread, state.unreadCount, state.unreadCount)
    val channels = pluralStringResource(R.plurals.feed_channels, state.channelCount, state.channelCount)
    return if (isLargeFontScale()) unread else stringResource(R.string.feed_summary, unread, channels)
}

/** The new posts, then the divider or "You're all caught up", then the read posts, keyed by post. */
@Composable
private fun FeedList(
    content: FeedContent.Posts,
    error: DomainError?,
    ads: ImmutableMap<Long, FeedAd>,
    onEvent: (FeedEvent) -> Unit,
    contentPadding: PaddingValues,
    openPostKey: String? = null,
    isRefreshing: Boolean = false,
    reactionChoice: FeedReactionChoice? = null,
) {
    val viewport = remember { LayoutBoundsHolder() }
    // Each channel's ad goes after its newest card, the first of the channel in the list.
    val adAfter =
        remember(content, ads) {
            (content.unread + content.read)
                .distinctBy(FeedPost::chatId)
                .mapNotNull { first -> ads[first.chatId]?.let { first.key to it } }
                .toMap()
        }
    val style =
        CardStyle(
            MaterialTheme.sizes.contentMaxWidth,
            MaterialTheme.spacing.medium,
            MaterialTheme.motionScheme,
            openPostKey,
            reactionChoice,
        )
    RefreshingFeedList(
        isRefreshing = isRefreshing,
        onRefresh = { keys, pulled -> onEvent(FeedEvent.Refresh(keys, pulled)) },
        viewport = viewport,
        contentPadding = contentPadding,
    ) {
        if (error != null) {
            item(key = ERROR, contentType = ERROR) {
                FeedErrorCard(error, onRetry = { onEvent(FeedEvent.Retry) }, Modifier.card(this, style))
            }
        }
        posts(content.unread, adAfter, onEvent, style, viewport)
        if (content.unread.isNotEmpty() && content.unread.none(FeedPost::isUnread)) {
            item(key = CAUGHT_UP, contentType = CAUGHT_UP) {
                FeedCaughtUp(content.readHere, hasOlder = content.read.isNotEmpty(), Modifier.card(this, style))
            }
        } else if (content.read.isNotEmpty()) {
            item(key = EARLIER, contentType = EARLIER) { FeedDivider(Modifier.card(this, style)) }
        }
        posts(content.read, adAfter, onEvent, style, viewport)
    }
}

/**
 * [posts] as cards, each followed by the ad of [adAfter] keyed by it; an unread one 60% inside [viewport] for a second
 * is reported seen, and an ad wholly inside it is reported viewed.
 */
private fun LazyListScope.posts(
    posts: ImmutableList<FeedPost>,
    adAfter: Map<String, FeedAd>,
    onEvent: (FeedEvent) -> Unit,
    style: CardStyle,
    viewport: LayoutBoundsHolder,
) {
    posts.forEach { post ->
        item(key = post.key, contentType = POST) {
            val seen =
                if (post.isUnread) {
                    Modifier.onVisibilityChanged(SEEN_MILLIS, SEEN_FRACTION, viewport) { visible ->
                        if (visible) onEvent(FeedEvent.Seen(post.chatId, post.messageIds))
                    }
                } else {
                    Modifier
                }
            FeedPostWithMenu(
                post = post,
                onEvent = onEvent,
                modifier = Modifier.card(this, style).then(seen),
                selected = post.key == style.openPostKey,
                reactions = style.reactionChoice?.takeIf { it.key == post.key }?.emojis,
            )
        }
        adAfter[post.key]?.let { ad ->
            item(key = ad.key, contentType = AD) {
                val viewed =
                    Modifier.onVisibilityChanged(minFractionVisible = 1f, viewportBounds = viewport) { visible ->
                        if (visible) onEvent(FeedAdEvent.Viewed(ad.chatId, ad.id))
                    }
                FeedAdCard(ad, post.channelTitle, onEvent, Modifier.card(this, style).then(viewed))
            }
        }
    }
}

/**
 * How the list's entries sit: at most [maxWidth] wide, [margin] in from the sheet's sides, on [motionScheme]; the post
 * keyed [openPostKey], open beside the feed, shows selected.
 */
private class CardStyle(
    val maxWidth: Dp,
    val margin: Dp,
    val motionScheme: MotionScheme,
    val openPostKey: String?,
    val reactionChoice: FeedReactionChoice?,
)

/** An entry of [scope], new posts sliding in on the spatial spring, at most [CardStyle.maxWidth] wide. */
private fun Modifier.card(
    scope: LazyItemScope,
    style: CardStyle,
): Modifier =
    with(scope) {
        this@card
            .animateItem(
                fadeInSpec = style.motionScheme.defaultEffectsSpec(),
                placementSpec = style.motionScheme.defaultSpatialSpec(),
                fadeOutSpec = style.motionScheme.fastEffectsSpec(),
            ).widthIn(max = style.maxWidth)
            .fillMaxWidth()
            .padding(horizontal = style.margin)
    }

/** Nothing new: an empty state with "Back to chats" ([onBack]) and "Show read posts". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NoNewPosts(
    onEvent: (FeedEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie12Sided,
        icon = UiR.drawable.ic_dynamic_feed,
        title = stringResource(R.string.feed_empty_title),
        body = stringResource(R.string.feed_empty_body),
        modifier = modifier,
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onBack, shapes = ButtonDefaults.shapes()) {
                    Text(
                        stringResource(R.string.feed_back_to_chats),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
                TextButton(onClick = { onEvent(FeedEvent.ShowReadPosts) }) {
                    Text(stringResource(R.string.feed_show_read))
                }
            }
        },
    )
}

/** Reading failed with nothing to show: the error on `Cookie4Sided`, with "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeedFailed(
    error: DomainError,
    onEvent: (FeedEvent) -> Unit,
    modifier: Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.feed_error_title),
        body = errorText(error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = { onEvent(FeedEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.feed_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

@PingogramPreviews
@Composable
private fun FeedScreenPreview(
    @PreviewParameter(FeedPreviewStates::class) state: FeedUiState,
) {
    PingogramPreview { FeedScreen(state = state, onEvent = {}, onBack = {}) }
}

// Keys and content types of the list's entries; a post's key is its chat's and its first message's id.
private const val POST = "post"
private const val AD = "ad"
private const val ERROR = "error"
private const val EARLIER = "earlier"
private const val CAUGHT_UP = "caughtUp"

/** A post counts as seen once this share of it has been on screen for [SEEN_MILLIS] (FR-X5). */
private const val SEEN_FRACTION = 0.6f
private const val SEEN_MILLIS = 1_000L
