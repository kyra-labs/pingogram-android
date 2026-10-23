package dev.kyralabs.pingogram.feature.chat.ui.inline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButton
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.InlineBotEvent
import dev.kyralabs.pingogram.feature.chat.ui.bot.isHardwareEnter
import dev.kyralabs.pingogram.feature.chat.ui.composer.ScheduleDialog
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.InlinePreviewData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The bot's button above its results ([InlineResultsButton]): a `FilledTonalButton` with the press morph, centred, its
 * text the bot's ("Sign in to continue"), as user content.
 */
@Composable
internal fun ResultsButton(
    button: InlineResultsButton,
    onClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
            Text(
                text = button.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.userContent,
            )
        }
    }
}

/** The first page on its way: a loading indicator after a moment, in a row of cells' height so nothing jumps. */
@Composable
internal fun LoadingContent() {
    Box(
        Modifier.fillMaxWidth().heightIn(min = MaterialTheme.sizes.sharedMediaCell),
        contentAlignment = Alignment.Center,
    ) {
        DelayedLoadingIndicator()
    }
}

/** "No results" under the bot's [button], if any, in `bodyMedium` `onSurfaceVariant`, a polite live region. */
@Composable
internal fun EmptyContent(
    button: InlineResultsButton?,
    onEvent: (InlineBotEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(
        Modifier.fillMaxWidth().padding(spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        button?.let { ResultsButton(it) { onEvent(InlineBotEvent.OpenButton) } }
        Text(
            text = stringResource(R.string.inline_results_empty),
            modifier =
                Modifier
                    .padding(spacing.medium)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Why the answer failed, with "Try again" ([InlineBotEvent.Retry]); a flood wait says how long to wait, and "Try
 * again" waits with it.
 */
@Composable
internal fun FailedContent(
    failed: InlineResultsContent.Failed,
    onEvent: (InlineBotEvent) -> Unit,
) {
    val error = failed.error
    val text =
        if (error is DomainError.FloodWait && error.seconds <= 0) {
            stringResource(R.string.inline_results_failed)
        } else {
            errorText(error)
        }
    Box(Modifier.padding(MaterialTheme.spacing.small)) {
        ErrorSegment(text, failed.canRetry) { onEvent(InlineBotEvent.Retry) }
    }
}

/**
 * An error as global search shows one: the error icon, [text] and a "Try again" `TextButton` (enabled while
 * [canRetry]) in `onErrorContainer` on an `errorContainer` segment with `large` corners; a polite live region.
 */
@Composable
internal fun ErrorSegment(
    text: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = spacing.medium, end = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(UiR.drawable.ic_error), contentDescription = null)
            Text(
                text = text,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = spacing.medium)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry, enabled = canRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.inline_results_retry), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * A wavy indicator along the panel's top edge while [searching], after Material's 200 ms ([LoadingIndicatorDelay]),
 * as global search shows one; decorative, it takes no room.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SearchingIndicator(
    searching: Boolean,
    modifier: Modifier,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(searching) {
        shown = false
        if (searching) {
            delay(LoadingIndicatorDelay)
            shown = true
        }
    }
    if (shown) {
        LinearWavyProgressIndicator(
            modifier
                .fillMaxWidth()
                .wrapContentWidth()
                .padding(horizontal = MaterialTheme.spacing.large),
        )
    }
}
