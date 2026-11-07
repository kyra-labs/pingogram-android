package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The large header of a top-level tab (`docs/DESIGN_SYSTEM.md` §3.3, §6 "Chat list top bar", "Contacts list",
 * "Settings rows"; owner decisions 2026-11-06 and 2026-11-07), the one the Chats, Contacts and Settings tabs share:
 * Material's large flexible top bar built from its parts ([TwoRowsTopAppBar]), transparent on the screen's color,
 * which [scrollBehavior] (`exitUntilCollapsedScrollBehavior`) collapses as the content scrolls, in the layout and draw
 * phases.
 *
 * - **Expanded**: the [title] in `displaySmallEmphasized`, and under it the [subtitle] where there is one (the Chats
 *   tab's status line), the bar as tall as both at the current font scale ([tabHeaderExpandedHeight]).
 * - **Collapsed**: the dense bar's height (`sizes.denseTopAppBar`, §7) with the [title] in `titleLargeEmphasized`; the
 *   [subtitle] gets `expanded = false` to say what it shows there, if anything.
 * - [navigationIcon] at the start (selection mode's close) and [actions] at the end.
 *
 * It keeps the window margin ([windowMargin]), so its title lines up with the rows' content on wide windows, and is at
 * most `contentMaxWidth` wide, centred. [title] gets the style of the row it fills; [TabHeaderTitle] is the plain one.
 */
@Composable
fun TabHeader(
    scrollBehavior: TopAppBarScrollBehavior,
    title: @Composable (style: TextStyle) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable (expanded: Boolean) -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val typography = MaterialTheme.typography
    // The bar insets its title by the compact margin itself; on wider windows it moves in with the content's.
    val extraMargin = windowMargin() - MaterialTheme.spacing.windowMarginCompact
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        TwoRowsTopAppBar(
            title = { expanded ->
                title(if (expanded) typography.displaySmallEmphasized else typography.titleLargeEmphasized)
            },
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                    .padding(horizontal = extraMargin),
            subtitle = subtitle,
            navigationIcon = navigationIcon,
            actions = actions,
            collapsedHeight = MaterialTheme.sizes.denseTopAppBar,
            expandedHeight = tabHeaderExpandedHeight(withSubtitle = subtitle != null),
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            scrollBehavior = scrollBehavior,
        )
    }
}

/**
 * A tab's title as [TabHeader] shows it: [text] in [style], one line, a heading for screen readers; at a large font
 * scale a long title gets smaller rather than cut off.
 */
@Composable
fun TabHeaderTitle(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(maxFontSize = style.fontSize),
        style = style,
    )
}

/**
 * [TabHeader]'s expanded height: the collapsed row, then the title's line in `displaySmallEmphasized`, then
 * [withSubtitle] a line as tall as `labelLarge`'s (the Chats tab's status line, or the connection pill in its place),
 * and the room below them, in the current font scale.
 */
@Composable
fun tabHeaderExpandedHeight(withSubtitle: Boolean): Dp {
    val typography = MaterialTheme.typography
    val spacing = MaterialTheme.spacing
    val lines =
        with(LocalDensity.current) {
            val title = typography.displaySmallEmphasized.lineHeight.toDp()
            if (withSubtitle) title + typography.labelLarge.lineHeight.toDp() + spacing.extraSmall * 2 else title
        }
    return MaterialTheme.sizes.denseTopAppBar + lines + spacing.medium
}
