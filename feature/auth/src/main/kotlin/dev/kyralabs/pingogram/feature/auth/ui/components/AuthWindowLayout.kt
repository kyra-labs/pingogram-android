package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/** How the login screens use the window (`docs/DESIGN_SYSTEM.md` §7). */
internal enum class AuthWindowLayout {
    /** A phone in portrait: header and content scroll, and the actions stay at the bottom above the keyboard. */
    PinnedActions,

    /**
     * A compact-width window at 150% font scale and more: header, content and actions in one centered column that
     * scrolls as a whole. Pinned actions would leave little room for the content there.
     */
    Centered,

    /**
     * Medium and wider windows, which include phones in landscape (compact height): the header in a pane at the start,
     * the form with its actions in one at the end, so the primary action stays in view.
     */
    TwoPane,
}

@Composable
internal fun authWindowLayout(): AuthWindowLayout {
    val isLargeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    return when {
        !isCompactWidthWindow() -> AuthWindowLayout.TwoPane
        isLargeFont -> AuthWindowLayout.Centered
        else -> AuthWindowLayout.PinnedActions
    }
}

/** Whether the window is less than 600 dp wide, e.g. a phone in portrait. */
@Composable
internal fun isCompactWidthWindow(): Boolean =
    !currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )

/** Whether the window is less than 480 dp tall, e.g. a phone in landscape: artwork gets smaller there. */
@Composable
internal fun isCompactHeightWindow(): Boolean =
    !currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
    )

/** The horizontal window margin: 16 dp on compact windows, 24 dp on medium and wider ones (§7). */
@Composable
internal fun authHorizontalMargin(): Dp =
    if (isCompactWidthWindow()) MaterialTheme.spacing.windowMarginCompact else MaterialTheme.spacing.windowMarginMedium

private const val LARGE_FONT_SCALE = 1.5f

/**
 * The adaptive frame of every login screen (`docs/DESIGN_SYSTEM.md` §7):
 * - on a phone in portrait ([AuthWindowLayout.PinnedActions]) [header] and [content] scroll in one column, and
 *   [actions] stay at the bottom, above the keyboard;
 * - on medium and wider windows, phones in landscape included ([AuthWindowLayout.TwoPane]), [header] is a pane at the
 *   start and [content] one at the end with [actions] right below it, side by side in the middle of the window. Each
 *   pane scrolls on its own, and when [content] does not fit it scrolls above [actions], which stay at the bottom of
 *   the window: the primary action is always in view;
 * - on compact windows at large font scales ([AuthWindowLayout.Centered]) everything forms one column, at most
 *   `contentMaxWidth` wide, that scrolls as a whole.
 *
 * Nothing has a fixed height, so text never clips.
 *
 * @param horizontalAlignment how [header], [content] and [actions] align their children.
 * @param centerVertically center [header] and [content] vertically when they are shorter than the space for them (in
 *   two panes: center the panes on each other); the centered layout always does.
 */
@Composable
internal fun AuthPaneLayout(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    centerVertically: Boolean = false,
    header: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val margin = authHorizontalMargin()
    when (authWindowLayout()) {
        AuthWindowLayout.PinnedActions -> {
            Column(modifier = modifier.fillMaxSize()) {
                ScrollingColumn(
                    modifier = Modifier.weight(1f),
                    horizontalMargin = margin,
                    centerVertically = centerVertically,
                    horizontalAlignment = horizontalAlignment,
                ) {
                    header()
                    content()
                }
                ActionsColumn(
                    modifier = Modifier.padding(horizontal = margin, vertical = spacing.large),
                    horizontalAlignment = horizontalAlignment,
                    content = actions,
                )
            }
        }

        AuthWindowLayout.Centered -> {
            ScrollingColumn(
                modifier = modifier.fillMaxSize(),
                horizontalMargin = margin,
                centerVertically = true,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.large),
                    horizontalAlignment = horizontalAlignment,
                ) {
                    header()
                    content()
                    ActionsColumn(
                        modifier = Modifier.padding(top = spacing.small),
                        horizontalAlignment = horizontalAlignment,
                        content = actions,
                    )
                }
            }
        }

        AuthWindowLayout.TwoPane -> {
            TwoPanes(
                modifier = modifier.padding(horizontal = margin),
                horizontalAlignment = horizontalAlignment,
                centerVertically = centerVertically,
                header = header,
                actions = actions,
                content = content,
            )
        }
    }
}

/**
 * [header] at the start, [content] with [actions] at the end, side by side in the middle of the window. Each pane is
 * as tall as its content, at most as tall as the window; the end pane's [content] scrolls above [actions] when it does
 * not fit, so the actions never leave the window.
 */
@Composable
private fun TwoPanes(
    horizontalAlignment: Alignment.Horizontal,
    centerVertically: Boolean,
    header: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val paneGap = spacing.extraLarge
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth * 2 + paneGap).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(paneGap),
            verticalAlignment = if (centerVertically) Alignment.CenterVertically else Alignment.Top,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = spacing.large),
                verticalArrangement = Arrangement.spacedBy(spacing.large),
                horizontalAlignment = horizontalAlignment,
                content = header,
            )
            Column(modifier = Modifier.weight(1f)) {
                // Only as tall as the form, so the actions follow it; at most what the actions leave, then it scrolls.
                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.large),
                    horizontalAlignment = horizontalAlignment,
                    content = content,
                )
                ActionsColumn(
                    modifier = Modifier.padding(vertical = spacing.large),
                    horizontalAlignment = horizontalAlignment,
                    content = actions,
                )
            }
        }
    }
}

/** A step's actions, one above the other. */
@Composable
private fun ActionsColumn(
    modifier: Modifier,
    horizontalAlignment: Alignment.Horizontal,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/** A column that scrolls when its content is taller than the viewport and can center it when it is shorter. */
@Composable
private fun ScrollingColumn(
    modifier: Modifier,
    horizontalMargin: Dp,
    centerVertically: Boolean,
    horizontalAlignment: Alignment.Horizontal,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MaterialTheme.spacing
    BoxWithConstraints(modifier = modifier) {
        // At least as tall as the viewport, so short content can be centered and long content scrolls.
        val minHeight = if (constraints.hasBoundedHeight) maxHeight else Dp.Unspecified
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = horizontalMargin, vertical = spacing.large),
            verticalArrangement =
                Arrangement.spacedBy(
                    spacing.large,
                    if (centerVertically) Alignment.CenterVertically else Alignment.Top,
                ),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
