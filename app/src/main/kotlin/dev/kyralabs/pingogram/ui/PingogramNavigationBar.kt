package dev.kyralabs.pingogram.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.component.floatingNavigationBarHeight
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * [PingogramNavigationBar] while [visible] (`docs/DESIGN_SYSTEM.md` §6.1): it slides in from below by its own height on
 * the motion scheme's default spatial spring while it fades in on the default effects spring, and slides away and fades
 * out on the fast ones. The slide and the fade are read in the draw phase, so they never recompose or relayout.
 *
 * Hidden, it stays composed, but is neither placed (drawn, touched) nor in the semantics tree. Screens pushed above a
 * tab hide it, and composing it anew on the way back, its labels measured again ([labelsFit]), would add to the frame
 * that composes the tab's screen again, at the start of the back transition.
 *
 * [selected] is the tab the user is on, also while a screen pushed above it hides the bar, so the bar slides away and
 * back as it was instead of deselecting its tab on the way out and selecting it again on the way in. [accountMenu] is
 * the Settings tab's ([PingogramNavigationBar]).
 */
@Composable
internal fun FloatingNavigationBar(
    visible: Boolean,
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    accountMenu: AccountMenuActions? = null,
    newChat: (@Composable () -> Unit)? = null,
) {
    val motionScheme = MaterialTheme.motionScheme
    // 1 while shown, 0 while hidden: how far the bar is in its place, and its alpha.
    val position = remember { Animatable(if (visible) 1f else 0f) }
    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    val placed = remember { derivedStateOf { alpha.value > 0f } }
    LaunchedEffect(visible) {
        val target = if (visible) 1f else 0f
        launch {
            position.animateTo(
                target,
                if (visible) motionScheme.defaultSpatialSpec() else motionScheme.fastSpatialSpec(),
            )
        }
        alpha.animateTo(target, if (visible) motionScheme.defaultEffectsSpec() else motionScheme.fastEffectsSpec())
    }
    PingogramNavigationBar(
        selected = selected,
        onSelect = onSelect,
        accountMenu = accountMenu,
        newChat = newChat,
        modifier =
            modifier
                .placedWhile { visible || placed.value }
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                .graphicsLayer {
                    translationY = (1f - position.value) * size.height
                    this.alpha = alpha.value.coerceIn(0f, 1f)
                },
    )
}

/** Places this content only while [placed]; otherwise it is measured but neither drawn nor reached by touches. */
private fun Modifier.placedWhile(placed: () -> Boolean): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { if (placed()) placeable.place(0, 0) }
    }

/**
 * The navigation bar of compact windows (`docs/DESIGN_SYSTEM.md` §6.1): a pill that floats over the content and holds
 * the [TopLevelDestination]s as tabs. It takes less height than a navigation bar and keeps the tabs within thumb's
 * reach. Search opens from the Chats tab's header (owner decision 2026-11-06).
 *
 * Material has no floating navigation bar component. The pill takes the floating toolbar's standard colors, shape and
 * elevation; Material's [HorizontalFloatingToolbar] itself is at least 64 dp tall, so the pill is a [Surface] of
 * `sizes.floatingNavigationBar` (56 dp). The tabs are built from tokens (owner decision 2026-11-07): the selected tab
 * shows its filled icon and label on a `secondaryContainer` pill, the others their label. Where the labels do not fit
 * (narrow phones, large font scales: measured, never cut, [labelsFit]), the other tabs show their outlined icon
 * instead, named for screen readers by the label. With an [accountMenu], a long press on the Settings tab opens it
 * ([AccountMenuAnchor]).
 *
 * [newChat] (the Chats tab's "New chat", [BarNewChatButton]) sits `spacing.small` beside the pill, at its end. The pill
 * stays centred in the window; only where it and the button's room would not fit side by side does it move toward the
 * start, by what is missing, on every tab alike, so switching tabs never moves it.
 *
 * In the experimental look (`LocalExperimentalUi`, `docs/DESIGN_SYSTEM.md` §13) the pill is
 * `sizes.experimentalNavigationBar` (64 dp) tall, its selected tab a `primary` pill with its label, the other tabs
 * their icons only (Owner decision to confirm (2026-11-07): the standard look shows labels where they fit), and the
 * New chat button beside it a 64 dp rounded square.
 */
@Composable
internal fun PingogramNavigationBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    accountMenu: AccountMenuActions? = null,
    newChat: (@Composable () -> Unit)? = null,
) {
    val colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
    val gap = MaterialTheme.spacing.small
    val experimental = LocalExperimentalUi.current
    val sizes = MaterialTheme.sizes
    val buttonWidth = if (experimental) sizes.experimentalFab else sizes.floatingNavigationBar
    val buttonRoom = buttonWidth + gap
    BoxWithConstraints(modifier.padding(horizontal = FloatingToolbarDefaults.ScreenOffset)) {
        // The New chat button's room is kept on every tab, so changing tabs never switches labels and icons. The
        // experimental look's other tabs show their icons only, as the owner's design draws them (§13).
        val showLabels = !experimental && labelsFit(maxWidth - buttonRoom)
        PillAndButton(gap, buttonWidth) {
            Surface(
                shape = FloatingToolbarDefaults.ContainerShape,
                color = colors.toolbarContainerColor,
                contentColor = colors.toolbarContentColor,
                shadowElevation = FloatingToolbarDefaults.ContainerExpandedElevationWithFab,
            ) {
                NavigationTabs(selected, onSelect, showLabels, accountMenu)
            }
            newChat?.invoke()
        }
    }
}

/**
 * The pill, then the New chat button `gap` after it where there is one: the pill centred in the width, moving toward
 * the start only by what the two would otherwise miss. The button's room ([buttonWidth]) counts on every tab, so the
 * pill sits in the same place whether the button shows or not (owner decision to confirm, 2026-11-07: with the labels
 * back, a 411 dp phone leaves the pill a little off centre on every tab rather than moving it on the Chats tab).
 */
@Composable
private fun PillAndButton(
    gap: Dp,
    buttonWidth: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val pill = measurables[0].measure(loose)
        val button = measurables.getOrNull(1)?.measure(loose)
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val height = maxOf(pill.height, button?.height ?: 0)
        val centred = (width - pill.width) / 2
        val room = maxOf(button?.width ?: 0, buttonWidth.roundToPx())
        val missing = centred + pill.width + gapPx + room - width
        val pillX = (centred - missing.coerceAtLeast(0)).coerceAtLeast(0)
        layout(width, height) {
            pill.placeRelative(pillX, (height - pill.height) / 2)
            button?.placeRelative(pillX + pill.width + gapPx, (height - button.height) / 2)
        }
    }
}

/** The bar's tabs in its pill, the Settings tab with the [accountMenu] where there is one. */
@Composable
private fun NavigationTabs(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    showLabels: Boolean,
    accountMenu: AccountMenuActions?,
) {
    Row(
        Modifier
            .height(floatingNavigationBarHeight())
            .padding(horizontal = MaterialTheme.spacing.small)
            .selectableGroup(),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val isSelected = destination == selected
            AccountMenuAnchor(
                menu = accountMenu?.state?.takeIf { destination == TopLevelDestination.Settings },
                onSwitch = { accountMenu?.onSwitch?.invoke(it) },
                onAdd = { accountMenu?.onAdd?.invoke() },
                modifier = Modifier.fillMaxHeight(),
            ) { trigger ->
                NavigationTab(
                    destination = destination,
                    selected = isSelected,
                    showLabel = showLabels || isSelected,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.fillMaxHeight().then(trigger),
                )
            }
        }
    }
}

/**
 * One tab: a pill-shaped `Role.Tab` whose icon slides in when it is selected, and whose indicator grows along one axis
 * from the tab's center on the motion scheme's fast spatial spring while it fades in on the default effects spring, as
 * Material's navigation indicator does. The indicator's width and alpha are read in the draw phase and the icon's width
 * in the layout phase ([revealHorizontally]), so none of them recomposes.
 */
@Composable
private fun NavigationTab(
    destination: TopLevelDestination,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val colorScheme = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val label = stringResource(destination.label)
    val (indicatorColor, selectedContent) = selectedTabColors()
    val indicatorAlpha = animateFloatAsState(if (selected) 1f else 0f, motionScheme.defaultEffectsSpec())
    val indicatorWidth = animateFloatAsState(if (selected) 1f else 0f, motionScheme.fastSpatialSpec())
    val contentColor by animateColorAsState(
        targetValue = if (selected) selectedContent else LocalContentColor.current,
        animationSpec = motionScheme.defaultEffectsSpec(),
    )
    // The tab takes touches over the pill's whole height (56 dp, `REQUIREMENTS.md` §6.3), its indicator and ripple
    // drawn inset by the pill's padding.
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                ).padding(vertical = spacing.small)
                .clip(CircleShape)
                .indication(interactionSource, ripple())
                .tabIndicator(indicatorColor, width = { indicatorWidth.value }, alpha = { indicatorAlpha.value })
                .padding(horizontal = if (selected) spacing.large else spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconShown = selected || !showLabel
        val iconWidth = animateFloatAsState(if (iconShown) 1f else 0f, motionScheme.fastSpatialSpec())
        val iconAlpha = animateFloatAsState(if (iconShown) 1f else 0f, motionScheme.fastEffectsSpec())
        Row(
            modifier =
                Modifier
                    .revealHorizontally { iconWidth.value }
                    .graphicsLayer { alpha = iconAlpha.value },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (selected) destination.selectedIcon else destination.unselectedIcon),
                // Without a label, the icon names the tab.
                contentDescription = if (showLabel) null else label,
                tint = contentColor,
            )
            if (showLabel) Spacer(Modifier.width(spacing.small))
        }
        if (showLabel) {
            val typography = MaterialTheme.typography
            Text(
                text = label,
                color = contentColor,
                style = if (selected) typography.labelLargeEmphasized else typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/**
 * The selected tab's pill and its content: `secondaryContainer` / `onSecondaryContainer`, or in the experimental look
 * `primary` / `onPrimary` (`docs/DESIGN_SYSTEM.md` §13).
 */
@Composable
private fun selectedTabColors(): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return if (LocalExperimentalUi.current) {
        colors.primary to colors.onPrimary
    } else {
        colors.secondaryContainer to colors.onSecondaryContainer
    }
}

/**
 * The selected tab's pill in [color], drawn behind the tab at [width] (a fraction of the tab's width, from its center)
 * and [alpha], both read in the draw phase. The spring overshoots a little; the tab's clip keeps the pill within it.
 */
private fun Modifier.tabIndicator(
    color: Color,
    width: () -> Float,
    alpha: () -> Float,
): Modifier =
    drawBehind {
        val pillWidth = size.width * width().coerceAtLeast(0f)
        drawRoundRect(
            color,
            topLeft = Offset((size.width - pillWidth) / 2, 0f),
            size = Size(pillWidth, size.height),
            cornerRadius = CornerRadius(size.height / 2),
            alpha = alpha().coerceIn(0f, 1f),
        )
    }

/**
 * Whether every tab can show its label in [maxWidth], the bar's room beside the New chat button. Measured for the
 * widest selection (the tab that grows most when emphasized and given its icon and wider padding), so selecting another
 * tab never switches between labels and icons.
 */
@Composable
private fun labelsFit(maxWidth: Dp): Boolean {
    val textMeasurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val spacing = MaterialTheme.spacing
    val density = LocalDensity.current
    val labels = TopLevelDestination.entries.map { stringResource(it.label) }
    return remember(textMeasurer, typography, spacing, density, labels, maxWidth) {
        with(density) {
            fun width(
                label: String,
                emphasized: Boolean,
            ): Int {
                val style = if (emphasized) typography.labelLargeEmphasized else typography.labelLarge
                return textMeasurer.measure(label, style, maxLines = 1).size.width
            }
            val labelsWidth = labels.sumOf { width(it, emphasized = false) }.toDp()
            val emphasisGrowth = labels.maxOf { width(it, emphasized = true) - width(it, emphasized = false) }.toDp()
            val tabPadding = spacing.medium * 2 * (labels.size - 1) + spacing.large * 2
            val icon = ICON_SIZE + spacing.small
            val toolbarPadding = spacing.small * 2
            labelsWidth + emphasisGrowth + tabPadding + icon + toolbarPadding <= maxWidth
        }
    }
}

/**
 * Shows this content at [fraction] of its width (0 hides it, values past 1 leave room after it), clipped, the fraction
 * read in the layout phase. Unlike `AnimatedVisibility`'s horizontal expand and shrink, whose intrinsic width stays
 * the content's full width, its intrinsic width is the width it takes: [HorizontalFloatingToolbar] sizes itself from
 * its content's intrinsic width and aligns the content to its end, so while one tab's icon shrank and another's grew
 * the toolbar was wider than its content and pushed it towards the search button, moving the whole bar until the
 * shrinking icon was gone.
 */
private fun Modifier.revealHorizontally(fraction: () -> Float): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        val width = (placeable.width * fraction()).roundToInt().coerceAtLeast(0)
        layout(width, placeable.height) { placeable.placeRelative(0, 0) }
    }

/** Material Symbols are 24 dp (`docs/DESIGN_SYSTEM.md` §8). */
private val ICON_SIZE = 24.dp

@PingogramPreviews
@Composable
private fun PingogramNavigationBarPreview() {
    PingogramPreview {
        PingogramNavigationBar(selected = TopLevelDestination.Chats, onSelect = {})
    }
}
