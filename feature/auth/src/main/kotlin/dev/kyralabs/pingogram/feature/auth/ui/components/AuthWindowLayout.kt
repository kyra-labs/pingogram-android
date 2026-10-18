package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
    /** A phone in portrait: content scrolls, and the actions stay at the bottom above the keyboard. */
    PinnedActions,

    /**
     * Medium and wider windows, landscape, and large font scales: one column of comfortable width, centered, with the
     * actions after the content. At 150% font scale and more, pinned actions would leave little room for the content.
     */
    Centered,
}

@Composable
internal fun authWindowLayout(): AuthWindowLayout {
    val isLargeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    return if (isCompactWidthWindow() && !isCompactHeightWindow() && !isLargeFont) {
        AuthWindowLayout.PinnedActions
    } else {
        AuthWindowLayout.Centered
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
 * The adaptive frame of every login screen (`docs/DESIGN_SYSTEM.md` §7). On a phone in portrait
 * ([AuthWindowLayout.PinnedActions]) [content] scrolls and [actions] stay at the bottom, above the keyboard. On medium
 * and wider windows, in landscape and at large font scales ([AuthWindowLayout.Centered]), content and actions form one
 * column, at most `contentMaxWidth` wide and centered in the window, that scrolls as a whole when it does not fit.
 * Nothing has a fixed height, so text never clips.
 *
 * @param centerVertically center [content] vertically when it is shorter than the space for it; the centered layout
 *   always does.
 */
@Composable
internal fun AuthPaneLayout(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    centerVertically: Boolean = false,
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
                    content = content,
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = margin, vertical = spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
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
                    content()
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                        content = actions,
                    )
                }
            }
        }
    }
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
