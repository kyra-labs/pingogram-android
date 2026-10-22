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

/** The bot and the query, which a new query changes: the keyboard's pick and Escape start again with it. */
internal val InlineBotUiState.queryKey: String get() = "$username $query"

/**
 * What a hardware keyboard does with the results while the field has the focus ([onKeyEvent], from the field's key
 * events): the down arrow picks the next result ([highlighted]) and the up arrow the one before, Enter sends the one
 * picked, Escape hides the results until the query changes. Without a pick, Enter and the up arrow stay the field's.
 */
@Stable
internal class InlineResultsState {
    /** The result the arrow keys picked; -1 before they picked one. */
    var highlighted by mutableIntStateOf(NONE)
        private set

    /** The query typed when Escape hid the results; null while they are not hidden. */
    private var hiddenQuery by mutableStateOf<String?>(null)

    /** Whether [bot]'s results show: there is an inline query, and Escape did not hide it. */
    fun isShown(bot: InlineBotUiState?): Boolean = bot != null && bot.queryKey != hiddenQuery

    /** The query typed changed to [queryKey]: the arrow keys start again at the top, and hidden results show again. */
    fun onQuery(queryKey: String?) {
        highlighted = NONE
        if (queryKey != hiddenQuery) hiddenQuery = null
    }

    /** What the composer's field does first with its keys, for [inline]'s results ([onKeyEvent]). */
    fun keysFor(inline: InlineBotInput): (KeyEvent) -> Boolean = { event -> onKeyEvent(event, inline) }

    /** Handles [event] for [inline]'s results: true if it picked, sent or hid, so the field does not act on it too. */
    fun onKeyEvent(
        event: KeyEvent,
        inline: InlineBotInput,
    ): Boolean {
        val bot = inline.state?.takeIf { event.type == KeyEventType.KeyDown && isShown(it) } ?: return false
        val results = (bot.content as? InlineResultsContent.Results)?.results.orEmpty()
        return when {
            event.key == Key.Escape -> {
                hiddenQuery = bot.queryKey
                true
            }

            results.isEmpty() -> {
                false
            }

            event.key == Key.DirectionDown -> {
                highlighted = (highlighted + 1).coerceAtMost(results.lastIndex)
                true
            }

            event.key == Key.DirectionUp && highlighted >= 0 -> {
                highlighted -= 1
                true
            }

            event.isHardwareEnter() && highlighted in results.indices -> {
                val entry = results[highlighted]
                inline.onEvent(InlineBotEvent.Send(entry.queryId, entry.result.id))
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
