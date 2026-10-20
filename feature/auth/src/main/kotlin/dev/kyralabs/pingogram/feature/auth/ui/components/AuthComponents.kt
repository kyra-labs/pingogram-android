package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.ui.AuthUiState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Whether Telegram asks to wait before the next request (`FLOOD_WAIT`, [AuthUiState.floodWaitSeconds]): every step's
 * badge then shows an error ([AuthHeadlineBadge]). Provided by `AuthScreen`.
 */
internal val LocalAuthFloodWait = compositionLocalOf { false }

/**
 * The layout of a login step in the adaptive [AuthPaneLayout]. Its header: a [MaterialShapes] badge with an icon
 * ([badgeShape], [badgeIcon]; [AuthHeadlineBadge]), the title (`headlineMediumEmphasized`, `docs/DESIGN_SYSTEM.md`
 * §3.3) as a heading, an optional description and [explanation]. Then [content], the step's form, and its actions as
 * Google's sign-in places them ([AuthActionRow]): [primaryAction] at the bottom end, [secondaryAction] at the bottom
 * start. In two panes the header is the start pane, the form with its actions the end one.
 *
 * @param isError whether the step shows an error, e.g. a wrong code: the badge turns into an error badge, and so it
 *   does while Telegram asks to wait ([LocalAuthFloodWait]).
 */
@Composable
internal fun AuthStepLayout(
    title: String,
    badgeShape: RoundedPolygon,
    @DrawableRes badgeIcon: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
    isError: Boolean = false,
    primaryAction: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
    explanation: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    AuthPaneLayout(
        modifier = modifier,
        header = {
            AuthHeadlineBadge(
                shape = badgeShape,
                icon = badgeIcon,
                isError = isError || LocalAuthFloodWait.current,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            explanation()
        },
        actions = { AuthActionRow(primary = primaryAction, secondary = secondaryAction) },
        content = content,
    )
}

/**
 * The decorative badge above a step's headline: [icon] on [shape], one of the soft [MaterialShapes], in
 * `primaryContainer`. While [isError] it morphs into [MaterialShapes.Cookie4Sided] (the spatial spring of
 * `MaterialTheme.motionScheme`) and fades to `errorContainer` with the error icon (the effects spring). Shape, colors
 * and icons are read while drawing, so the animation never recomposes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AuthHeadlineBadge(
    shape: RoundedPolygon,
    @DrawableRes icon: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val badgeShape =
        rememberMorphShape(
            start = shape,
            end = MaterialShapes.Cookie4Sided,
            morphed = isError,
            animationSpec = motion.defaultSpatialSpec(),
        )
    val error = animateFloatAsState(if (isError) 1f else 0f, motion.defaultEffectsSpec(), label = "BadgeError")
    val iconPainter = painterResource(icon)
    val errorPainter = painterResource(UiR.drawable.ic_error)
    val container = colors.primaryContainer
    val onContainer = colors.onPrimaryContainer
    val errorContainer = colors.errorContainer
    val onErrorContainer = colors.onErrorContainer
    Box(
        modifier =
            modifier
                .size(MaterialTheme.sizes.headlineBadge)
                .drawBehind {
                    val progress = error.value
                    drawOutline(
                        outline = badgeShape.createOutline(size, layoutDirection, this),
                        color = lerp(container, errorContainer, progress),
                    )
                    val iconSize = size * BADGE_ICON_FRACTION
                    translate((size.width - iconSize.width) / 2, (size.height - iconSize.height) / 2) {
                        if (progress < 1f) {
                            with(iconPainter) {
                                draw(
                                    size = iconSize,
                                    alpha = 1f - progress,
                                    colorFilter = ColorFilter.tint(lerp(onContainer, onErrorContainer, progress)),
                                )
                            }
                        }
                        if (progress > 0f) {
                            with(errorPainter) {
                                draw(
                                    size = iconSize,
                                    alpha = progress,
                                    colorFilter = ColorFilter.tint(onErrorContainer),
                                )
                            }
                        }
                    }
                },
    )
}

private const val BADGE_ICON_FRACTION = 0.5f

/**
 * A step's actions as Google's sign-in places them: [primary] at the end and [secondary] at the start of one row. When
 * both do not fit side by side (narrow windows, large font scales), [primary] stays at the end and [secondary] moves
 * to the start of the line below.
 */
@Composable
internal fun AuthActionRow(
    primary: (@Composable () -> Unit)?,
    secondary: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val gap = MaterialTheme.spacing.small
    Layout(
        contents = listOf(primary ?: {}, secondary ?: {}),
        modifier = modifier.fillMaxWidth(),
    ) { (primaryMeasurables, secondaryMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val primaryPlaceable = primaryMeasurables.firstOrNull()?.measure(loose)
        val secondaryPlaceable = secondaryMeasurables.firstOrNull()?.measure(loose)
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val primaryWidth = primaryPlaceable?.width ?: 0
        val primaryHeight = primaryPlaceable?.height ?: 0
        val secondaryHeight = secondaryPlaceable?.height ?: 0
        val fitsOneLine =
            primaryPlaceable == null ||
                secondaryPlaceable == null ||
                primaryWidth + gapPx + secondaryPlaceable.width <= width
        if (fitsOneLine) {
            val height = max(primaryHeight, secondaryHeight)
            layout(width, height) {
                secondaryPlaceable?.placeRelative(0, (height - secondaryHeight) / 2)
                primaryPlaceable?.placeRelative(width - primaryWidth, (height - primaryHeight) / 2)
            }
        } else {
            layout(width, primaryHeight + gapPx + secondaryHeight) {
                primaryPlaceable.placeRelative(width - primaryWidth, 0)
                secondaryPlaceable.placeRelative(0, primaryHeight + gapPx)
            }
        }
    }
}

/**
 * A step's main action: a default-height (40 dp) filled button with the Expressive press morph
 * (`ButtonDefaults.shapes()`, a `motionScheme` spring) and a `labelLargeEmphasized` label; [AuthActionRow] puts it at
 * the bottom end. [hero] makes it the medium (56 dp) button of a hero moment instead, Welcome's "Start messaging"
 * (`docs/DESIGN_SYSTEM.md` §6). While its request runs ([inProgress]) it shows a [LoadingIndicator] in place of the
 * label, keeping its width, and keeps [text] for screen readers. [interactionSource] lets the screen react to presses
 * too.
 */
@Composable
internal fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    inProgress: Boolean,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
) {
    val height = if (hero) ButtonDefaults.MediumContainerHeight else ButtonDefaults.MinHeight
    Button(
        onClick = { if (!inProgress) onClick() },
        shapes = if (hero) ButtonDefaults.shapesFor(height) else ButtonDefaults.shapes(),
        modifier =
            modifier
                .heightIn(min = height)
                .semantics { if (inProgress) contentDescription = text },
        enabled = enabled || inProgress,
        contentPadding = if (hero) ButtonDefaults.contentPaddingFor(height) else ButtonDefaults.ContentPadding,
        interactionSource = interactionSource,
    ) {
        ButtonLabel(
            text = text,
            style = MaterialTheme.typography.labelLargeEmphasized,
            inProgress = inProgress,
            indicatorSize = ButtonDefaults.iconSizeFor(height),
        )
    }
}

/**
 * A secondary action: a text button with a `labelLarge` label (`docs/DESIGN_SYSTEM.md` §3.3), e.g. "Forgot password?"
 * at the bottom start of a step, or "Resend code" in its form; shows progress like [AuthPrimaryButton].
 */
@Composable
internal fun AuthTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inProgress: Boolean = false,
) {
    val height = ButtonDefaults.MinHeight
    TextButton(
        onClick = { if (!inProgress) onClick() },
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.semantics { if (inProgress) contentDescription = text },
        enabled = enabled || inProgress,
    ) {
        ButtonLabel(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            inProgress = inProgress,
            indicatorSize = ButtonDefaults.iconSizeFor(height),
        )
    }
}

/**
 * A button's label, or while [inProgress] a [LoadingIndicator] over the hidden label, so the button keeps its width;
 * the hidden label is left out for screen readers, which hear the button's description instead.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ButtonLabel(
    text: String,
    style: TextStyle,
    inProgress: Boolean,
    indicatorSize: Dp,
) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = style,
            modifier = if (inProgress) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
        )
        if (inProgress) {
            LoadingIndicator(modifier = Modifier.size(indicatorSize), color = LocalContentColor.current)
        }
    }
}

/** A notice inside a step, on a tonal surface: e.g. why a password cannot be recovered. */
@Composable
internal fun AuthNotice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(MaterialTheme.spacing.large),
        )
    }
}

/** A square whose side is [fraction] of the available width, at most [maxSize]: hero artwork, the QR code frame. */
internal fun Modifier.squareOfWidth(
    fraction: Float,
    maxSize: Dp,
): Modifier =
    layout { measurable, constraints ->
        val side = min((constraints.maxWidth * fraction).roundToInt(), maxSize.roundToPx())
        val placeable = measurable.measure(Constraints.fixed(side, side))
        layout(side, side) { placeable.place(0, 0) }
    }

/** As wide as tall, the larger of the content's width and height, with the content centered: a badge. */
internal fun Modifier.squareAroundContent(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val side = max(placeable.width, placeable.height)
        layout(side, side) { placeable.place((side - placeable.width) / 2, (side - placeable.height) / 2) }
    }
