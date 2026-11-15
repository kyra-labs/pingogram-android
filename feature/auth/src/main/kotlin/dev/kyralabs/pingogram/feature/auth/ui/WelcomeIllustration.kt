package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.AvatarColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPaneLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.isCompactWidthWindow
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.R as UiR

// Welcome's illustration (docs/DESIGN_SYSTEM.md §6 "Welcome", board 23).

/**
 * Welcome's illustration (board 23): Pingogram's logo ([WelcomeLogo]) among chat avatars in the shapes of their kinds
 * (people round, a group [MaterialShapes.Cookie9Sided], a channel [MaterialShapes.Cookie6Sided]) on the vivid avatar
 * fills, and a sample message on each side, all decorative. As wide as the window up to
 * `welcomeIllustrationMaxSize`, and shorter in short windows and at large font scales, where it shrinks first (to
 * `illustrationMinSize`), so the text and "Start messaging" stay in view. Its text keeps its size whatever the font
 * scale: it is part of the picture, which screen readers skip.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WelcomeIllustration(
    isMorphed: Boolean,
    onHold: () -> Unit,
) {
    val sizes = MaterialTheme.sizes
    val density = LocalDensity.current
    val windowHeight =
        with(density) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val heightShare = WINDOW_HEIGHT_SHARE / max(1f, density.fontScale)
    val maxSide = (windowHeight * heightShare).coerceIn(sizes.illustrationMinSize, sizes.welcomeIllustrationMaxSize)
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Layout(
            modifier = Modifier.clearAndSetSemantics {},
            content = { IllustrationItems(isMorphed, onHold) },
        ) { measurables, constraints ->
            // Laid out at the board's size, then scaled as one picture, text included, to the side it gets.
            val board = sizes.welcomeIllustrationMaxSize.roundToPx()
            val side = min(constraints.maxWidth, maxSide.roundToPx())
            val scale = side.toFloat() / board
            layout(side, (side * ILLUSTRATION_ASPECT).roundToInt()) {
                measurables.forEachIndexed { index, measurable ->
                    val spot = IllustrationSpots[index]
                    val placeable =
                        if (spot.size > 0f) {
                            val px = (board * spot.size).roundToInt()
                            measurable.measure(Constraints.fixed(px, px))
                        } else {
                            measurable.measure(Constraints(maxWidth = board))
                        }
                    // Scaled about its centre: moved so that its scaled box starts at its place.
                    val x = side * spot.x - placeable.width * (1 - scale) / 2
                    val y = side * spot.y - placeable.height * (1 - scale) / 2
                    placeable.placeRelativeWithLayer(x.roundToInt(), y.roundToInt()) {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = spot.rotation
                    }
                }
            }
        }
    }
}

/** [WelcomeIllustration]'s items, in the order of [IllustrationSpots]: the avatars, the messages, then the logo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IllustrationItems(
    isMorphed: Boolean,
    onHold: () -> Unit,
) {
    val palette = MaterialTheme.pingogramColors.vividAvatars
    val typography = MaterialTheme.typography
    SampleAvatar("👋", palette[TEAL], MaterialShapes.Cookie9Sided.toShape(), typography.headlineMedium)
    SampleAvatar(
        stringResource(R.string.auth_welcome_sample_initials_one),
        palette[ORANGE],
        CircleShape,
        typography.titleLargeEmphasized,
    )
    SampleAvatar("📣", palette[LILAC], MaterialShapes.Cookie6Sided.toShape(), typography.headlineSmall)
    SampleAvatar("🎉", palette[GOLD], MaterialShapes.Clover4Leaf.toShape(), typography.titleLarge)
    SampleAvatar(
        stringResource(R.string.auth_welcome_sample_initials_two),
        palette[BLUE],
        CircleShape,
        typography.titleMediumEmphasized,
    )
    SampleMessage(stringResource(R.string.auth_welcome_sample_incoming), outgoing = false)
    SampleMessage(stringResource(R.string.auth_welcome_sample_outgoing), outgoing = true)
    WelcomeLogo(isMorphed = isMorphed, onHold = onHold)
}

/** Where an item of [WelcomeIllustration] goes: fractions of its width, [size] 0 for a message's own size. */
private class Spot(
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float = 0f,
)

/** The items' places, in the order [WelcomeIllustration] composes them, from the owner's board (412 wide). */
private val IllustrationSpots =
    listOf(
        Spot(x = 34f / BOARD_WIDTH, y = 70f / BOARD_WIDTH, size = 92f / BOARD_WIDTH, rotation = -8f),
        Spot(x = 282f / BOARD_WIDTH, y = 48f / BOARD_WIDTH, size = 70f / BOARD_WIDTH),
        Spot(x = 296f / BOARD_WIDTH, y = 262f / BOARD_WIDTH, size = 84f / BOARD_WIDTH, rotation = 10f),
        Spot(x = 58f / BOARD_WIDTH, y = 292f / BOARD_WIDTH, size = 66f / BOARD_WIDTH),
        Spot(x = 214f / BOARD_WIDTH, y = 330f / BOARD_WIDTH, size = 48f / BOARD_WIDTH),
        Spot(x = 96f / BOARD_WIDTH, y = 24f / BOARD_WIDTH, size = 0f),
        Spot(x = 36f / BOARD_WIDTH, y = 372f / BOARD_WIDTH, size = 0f),
        Spot(x = 136f / BOARD_WIDTH, y = 120f / BOARD_WIDTH, size = 140f / BOARD_WIDTH),
    )

/** A sample chat's avatar: [content] (initials or an emoji) on its vivid fill in [shape]. */
@Composable
private fun SampleAvatar(
    content: String,
    colors: AvatarColors,
    shape: Shape,
    style: TextStyle,
) {
    Box(modifier = Modifier.background(colors.container, shape), contentAlignment = Alignment.Center) {
        Text(text = content, style = style, color = colors.content, maxLines = 1)
    }
}

/**
 * A sample message: an incoming one on `surfaceContainerHigh`, an outgoing one on `primary`, `large` corners with the
 * small one at the bottom end, as a bubble's last message.
 */
@Composable
private fun SampleMessage(
    text: String,
    outgoing: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large.copy(bottomEnd = MaterialTheme.shapes.extraSmall.bottomEnd)
    Text(
        text = text,
        style = if (outgoing) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
        color = if (outgoing) colors.onPrimary else colors.onSurface,
        maxLines = 1,
        modifier =
            Modifier
                .background(if (outgoing) colors.primary else colors.surfaceContainerHigh, shape)
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
    )
}

/**
 * Pingogram's mark (`art/`) in `onPrimary` on a `primary` [MaterialShapes.Cookie9Sided], one color of the theme
 * (`docs/DESIGN_SYSTEM.md` §8), that morphs into a soft burst while [isMorphed] (a fast spatial spring,
 * `docs/DESIGN_SYSTEM.md` §5: press feedback).
 *
 * Held for about a second ([DEMO_HOLD_MILLIS]), it calls [onHold] with a `LongPress` haptic: the hidden way into the
 * demo for app store reviewers (`docs/adr/0034-demo-mode-for-store-review.md`), whose instructions name it. Nothing on
 * screen shows it, and screen readers get no action for it. Never move the logo or change the gesture without the
 * owner's decision and the store instructions updated with it (`docs/PLAY_STORE.md` §1).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeLogo(
    isMorphed: Boolean,
    onHold: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val currentOnHold by rememberUpdatedState(onHold)
    val shape =
        rememberMorphShape(
            start = MaterialShapes.Cookie9Sided,
            end = MaterialShapes.SoftBurst,
            morphed = isMorphed,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        )
    Box(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.primary, shape)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // Held without lifting or moving away for the whole time: a press, not a tap or a scroll.
                        // Only the timeout gives null: a lift, or a scroll that takes the pointer, ends the gesture.
                        val ended = withTimeoutOrNull(DEMO_HOLD_MILLIS) { waitForUpOrCancellation() != null }
                        if (ended == null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnHold()
                        }
                    }
                }.clearAndSetSemantics { testTag = WELCOME_LOGO_TAG },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_pingogram),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxSize(LOGO_ICON_FRACTION),
        )
    }
}

/** The owner's board's width, which the illustration's places are fractions of. */
private const val BOARD_WIDTH = 412f

/** The illustration's height over its width, as on the board (420 by 412). */
private const val ILLUSTRATION_ASPECT = 420f / BOARD_WIDTH

/** The share of the window's height the illustration may take at 100% font, as on the board (420 of 915). */
private const val WINDOW_HEIGHT_SHARE = 0.46f

private const val LOGO_ICON_FRACTION = 0.51f

// The vivid avatar fills' places in VividAvatarPalette.
private const val ORANGE = 0
private const val TEAL = 2
private const val BLUE = 3
private const val LILAC = 4
private const val GOLD = 5

/** The logo's test tag: only a tag, so screen readers still get nothing from the decorative artwork. */
internal const val WELCOME_LOGO_TAG = "welcome_logo"

/** How long the logo is held to open the demo's question: "about a second", as the store instructions say. */
private const val DEMO_HOLD_MILLIS = 1_000L
