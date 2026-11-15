package dev.kyralabs.pingogram.feature.media.story

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** [content] shown while [visible], fading in on the default and out on the fast effects spring. */
@Composable
internal fun HeldFade(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
    ) {
        content()
    }
}

/**
 * While a finger holds the story (board 26): the pause symbol in `MaterialShapes.Cookie9Sided` on the media overlay,
 * over "Release to continue" on a pill of it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HeldBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.pingogramColors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(IconButtonDefaults.largeContainerSize())
                    .background(colors.mediaOverlay, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_pause_filled),
                contentDescription = null,
                modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                tint = colors.onMediaOverlay,
            )
        }
        Text(
            text = stringResource(R.string.story_release_to_continue),
            modifier =
                Modifier
                    .background(colors.mediaOverlay, CircleShape)
                    .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
            color = colors.onMediaOverlay,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Whether a screen reader explores by touch (TalkBack), following its changes. */
@Composable
internal fun rememberScreenReader(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.run { isEnabled && isTouchExplorationEnabled } == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
