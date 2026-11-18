package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.isReducedMotion
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.currentCoroutineContext

/**
 * The feed while its posts load (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", 5a): skeleton cards in the post cards'
 * shapes on `surfaceContainerLow`, pulsing on the effects spring in the draw phase and still with reduced motion; one
 * "Loading posts…" for screen readers.
 */
@Composable
internal fun FeedSkeleton(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.feed_loading)
    val pulse = remember { Animatable(1f) }
    if (!isReducedMotion()) {
        val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        LaunchedEffect(pulse, spec) {
            // Compose UI tests install a policy that cancels endless loops; elsewhere it pulses until it leaves.
            val policy = currentCoroutineContext()[InfiniteAnimationPolicy]
            if (policy == null) pulse.pulse(spec) else policy.onInfiniteOperation { pulse.pulse(spec) }
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = MaterialTheme.spacing.medium)
                .graphicsLayer { alpha = pulse.value }
                .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(CARDS) { SkeletonCard(withMedia = it % 2 == 0) }
    }
}

/** Fades between [REST_ALPHA] and full opacity on [spec] until cancelled. */
private suspend fun Animatable<Float, *>.pulse(spec: AnimationSpec<Float>): Nothing {
    while (true) {
        animateTo(REST_ALPHA, spec)
        animateTo(1f, spec)
    }
}

/** A post card's outline: avatar, name and time lines, text lines, a media block on every other card, and pills. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SkeletonCard(withMedia: Boolean) {
    val colors = MaterialTheme.colorScheme
    val bone = colors.surfaceContainerHighest
    val line =
        with(LocalDensity.current) {
            MaterialTheme.typography.bodyLarge.lineHeight
                .toDp()
        }
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            Modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .fillMaxWidth()
                .background(colors.surfaceContainerLow, MaterialTheme.shapes.extraLarge)
                .padding(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(MaterialTheme.sizes.avatarRow).background(bone, MaterialShapes.Cookie6Sided.toShape()))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Bone(bone, line, Modifier.fillMaxWidth(NAME_WIDTH))
                Bone(bone, line, Modifier.fillMaxWidth(TIME_WIDTH))
            }
        }
        Bone(bone, line, Modifier.fillMaxWidth())
        Bone(bone, line, Modifier.fillMaxWidth(LAST_LINE_WIDTH))
        if (withMedia) {
            Box(Modifier.fillMaxWidth().aspectRatio(MEDIA_ASPECT).background(bone, MaterialTheme.shapes.large))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            repeat(
                PILLS,
            ) { Bone(bone, line + spacing.small, Modifier.width(MaterialTheme.sizes.avatarMedium), CircleShape) }
        }
    }
}

/** One placeholder of [height], rounded as text is ([shape]). */
@Composable
private fun Bone(
    color: Color,
    height: Dp,
    modifier: Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(modifier.height(height).background(color, shape))
}

private const val CARDS = 3
private const val PILLS = 2
private const val REST_ALPHA = 0.5f
private const val NAME_WIDTH = 0.5f
private const val TIME_WIDTH = 0.3f
private const val LAST_LINE_WIDTH = 0.7f
private const val MEDIA_ASPECT = 16f / 9f
