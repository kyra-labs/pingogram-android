package dev.kyralabs.pingogram.feature.chat.ui.keyword

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.actionbar.LocalChatActionBar
import dev.kyralabs.pingogram.feature.chat.ui.listen.LocalListenBar
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The keyword match bar (board 30): at the top of the conversation's sheet, inset as its other bars, a tonal strip on
 * `surfaceContainerHigh` with `large` corners: the `sell` symbol in `tertiary`, "Keyword: launch · 1 of 4" in
 * `labelLarge` (the word ellipsized before the count and the buttons), then "Older match" (↑), "Newer match" (↓) and
 * "Close" as icon buttons, the arrows the same way in right-to-left layouts. Screen readers hear a pane, "Keyword
 * matches", with its labelled buttons; the count is a polite live region, "Match 2 of 4", so each step is heard while
 * the focus stays on the button pressed.
 */
@Composable
internal fun KeywordMatchBarRow(
    bar: KeywordMatchBar,
    onEvent: (KeywordMatchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val pane = stringResource(R.string.keyword_match_pane)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceContainerHigh)
                .semantics { paneTitle = pane }
                .padding(start = spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(UiR.drawable.ic_sell), contentDescription = null, tint = colors.tertiary)
        Row(
            modifier = Modifier.weight(1f).padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = MaterialTheme.typography.labelLarge.userContent
            Text(
                text = stringResource(R.string.keyword_match_word, bar.word.bidiIsolated()),
                modifier = Modifier.weight(1f, fill = false),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = style,
            )
            val spoken = stringResource(R.string.keyword_match_position_spoken, bar.position, bar.count)
            Text(
                text = stringResource(R.string.keyword_match_position, bar.position, bar.count),
                modifier =
                    Modifier.semantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
                color = colors.onSurface,
                maxLines = 1,
                style = style,
            )
        }
        BarButton(R.drawable.ic_keyboard_arrow_up, R.string.keyword_match_older, bar.hasOlder) {
            onEvent(KeywordMatchEvent.Older)
        }
        BarButton(UiR.drawable.ic_keyboard_arrow_down, R.string.keyword_match_newer, bar.hasNewer) {
            onEvent(KeywordMatchEvent.Newer)
        }
        BarButton(UiR.drawable.ic_close, R.string.keyword_match_close, enabled = true) {
            onEvent(KeywordMatchEvent.Close)
        }
    }
}

@Composable
private fun BarButton(
    @DrawableRes icon: Int,
    label: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

/**
 * The bar's slot for the conversation ([LocalKeywordMatchBar]), reading [viewModel]'s bar there alone, so a step
 * recomposes the bar and not the conversation; each step's message goes to [onShowMessage].
 */
@Composable
internal fun rememberKeywordMatchSlot(
    viewModel: KeywordMatchViewModel,
    onShowMessage: (Long) -> Unit,
): @Composable (PaddingValues) -> Unit {
    val bar = viewModel.bar.collectAsStateWithLifecycle()
    val currentOnShow by rememberUpdatedState(onShowMessage)
    LaunchedEffect(viewModel) { viewModel.showMessage.collect { currentOnShow(it) } }
    return remember(viewModel) { { padding -> KeywordMatchSlot(bar, viewModel::onEvent, padding) } }
}

/** The bar, growing in and shrinking away on the default spatial spring like the conversation's other bars. */
@Composable
private fun KeywordMatchSlot(
    bar: State<KeywordMatchBar?>,
    onEvent: (KeywordMatchEvent) -> Unit,
    padding: PaddingValues,
) {
    val motion = MaterialTheme.motionScheme
    val current = bar.value
    val last = remember { mutableStateOf(current) }
    SideEffect { if (current != null) last.value = current }
    val shown = current ?: last.value
    val direction = LocalLayoutDirection.current
    val small = MaterialTheme.spacing.small
    AnimatedVisibility(
        visible = current != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        shown?.let {
            KeywordMatchBarRow(
                bar = it,
                onEvent = onEvent,
                modifier =
                    Modifier.padding(
                        start = padding.calculateStartPadding(direction) + small,
                        top = padding.calculateTopPadding() + small,
                        end = padding.calculateEndPadding(direction) + small,
                    ),
            )
        }
    }
}

/** The conversation's action bar, keyword match bar and Listen to posts' player, where its route provides them. */
@Composable
internal fun ConversationCards(padding: PaddingValues) {
    LocalChatActionBar.current?.invoke(padding)
    LocalKeywordMatchBar.current?.invoke(padding)
    LocalListenBar.current?.invoke(padding)
}

/** The conversation's keyword match bar, provided by its route; null shows none. */
internal val LocalKeywordMatchBar = compositionLocalOf<(@Composable (PaddingValues) -> Unit)?> { null }

@PingogramPreviews
@Composable
private fun KeywordMatchBarPreview() {
    PingogramPreview {
        KeywordMatchBarRow(KeywordMatchBar("launch", messageId = 1, position = 1, count = 4), onEvent = {})
    }
}
