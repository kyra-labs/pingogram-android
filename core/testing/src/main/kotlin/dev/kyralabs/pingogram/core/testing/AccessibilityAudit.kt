package dev.kyralabs.pingogram.core.testing

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Checks what Accessibility Scanner checks on a device, in JVM tests (`docs/audit/accessibility.md`): Google's
 * Accessibility Test Framework turns itself off under Robolectric, so this walks the merged semantics tree of every
 * window (dialogs and sheets too) instead. Every control (a click, toggle or progress action) wholly on screen must
 * have a name screen readers read (its text, content description or editable text) and a touch target at least
 * [minTouchTarget] on each side (`REQUIREMENTS.md` §6.3), sliders [minSliderHeight] high (Material's slider is 44 dp
 * tall whatever it is given, and Compose's hit testing widens its touches to 48 dp). Controls cut by the window's edge
 * are skipped (a list scrolls them into view first), and so are links in text. Fails with every problem at once.
 */
fun SemanticsNodeInteractionsProvider.assertAccessible(
    minTouchTarget: Dp = 48.dp,
    minSliderHeight: Dp = 44.dp,
) {
    val roots = onAllNodes(isRoot()).fetchSemanticsNodes()
    val density = roots.first().layoutInfo.density
    val minPx = with(density) { minTouchTarget.toPx() }
    val minSliderPx = with(density) { minSliderHeight.toPx() }
    val problems = mutableListOf<String>()
    roots.forEach { it.checkControls(problems, minPx, minSliderPx) }
    check(problems.isEmpty()) { problems.joinToString(separator = "\n", prefix = "Accessibility problems:\n") }
}

/** Adds to [problems] what is wrong with the controls of this window's tree. */
private fun SemanticsNode.checkControls(
    problems: MutableList<String>,
    minPx: Float,
    minSliderPx: Float,
) {
    val density = layoutInfo.density
    val window = boundsInRoot
    walk { node ->
        if (!node.isControl()) return@walk
        val bounds = node.touchBoundsInRoot
        // Not placed (a list's prefetched item), or cut by a scrolling parent or the window's edge.
        val clipped = node.boundsInRoot
        if (clipped.width.toInt() != node.size.width || clipped.height.toInt() != node.size.height) return@walk
        if (!window.contains(clipped)) return@walk
        val name = node.spokenName()
        if (name.isNullOrBlank()) problems += "a control at $bounds has no name: ${node.config}"
        val minHeight = if (node.isSlider()) minSliderPx else minPx
        // A tolerance of half a pixel for rounding in the layout.
        if (bounds.width < minPx - HALF_PIXEL || bounds.height < minHeight - HALF_PIXEL) {
            val size = with(density) { "${bounds.width.toDp()} × ${bounds.height.toDp()}" }
            problems += "\"${name.orEmpty()}\" is $size, less than ${with(density) { minPx.toDp() }}"
        }
    }
}

private fun SemanticsNode.walk(visit: (SemanticsNode) -> Unit) {
    visit(this)
    children.forEach { it.walk(visit) }
}

/**
 * Whether this is an enabled control: something to click, toggle or adjust. Links in a text are not: screen readers
 * read them with their text, and inline links need no 48 dp target (WCAG 2.5.8).
 */
private fun SemanticsNode.isControl(): Boolean =
    config.none { (key, _) -> key.name == LINK_MARKER } &&
        config.getOrNull(SemanticsProperties.Disabled) == null &&
        (
            SemanticsActions.OnClick in config ||
                SemanticsProperties.ToggleableState in config ||
                SemanticsActions.SetProgress in config
        )

private fun SemanticsNode.isSlider(): Boolean =
    SemanticsActions.SetProgress in config && SemanticsActions.OnClick !in config

/** What a screen reader reads for this node: its content description, else its text, else what is typed in it. */
private fun SemanticsNode.spokenName(): String? =
    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")?.takeIf { it.isNotBlank() }
        ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.takeIf { it.isNotBlank() }
        ?: config.getOrNull(SemanticsProperties.EditableText)?.text?.takeIf { it.isNotBlank() }
        ?: config.getOrNull(SemanticsProperties.InputText)?.text

private fun Rect.contains(other: Rect): Boolean =
    other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

private const val HALF_PIXEL = 0.5f

// The semantics key Compose marks text links with (`LinkAnnotation`), not public.
private const val LINK_MARKER = "LinkTestMarker"
