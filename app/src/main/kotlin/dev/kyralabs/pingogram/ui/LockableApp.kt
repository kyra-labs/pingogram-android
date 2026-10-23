package dev.kyralabs.pingogram.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

/**
 * The app behind the passcode lock (`REQUIREMENTS.md` FR-S1): [content] while unlocked, [lockScreen] while [locked].
 *
 * Locked, [content] leaves the composition altogether rather than hiding under the lock screen: nothing of it can be
 * drawn, read by a screen reader, reached with a keyboard or seen in a dialog or menu it had open, and its screens stop
 * as in the background, so an open chat marks nothing as read (it views messages only while started). Its saved state
 * stays meanwhile ([rememberSaveableStateHolder]), as across a rotation: the back stack, scroll positions and open
 * dialogs come back on unlocking, and the view models, which the activity keeps, were never gone.
 *
 * Locking shows the lock screen at once (the app is usually out of sight then); unlocking composes [content] below it
 * and fades the lock screen out on the default effects spring.
 */
@Composable
internal fun LockableApp(
    locked: Boolean,
    lockScreen: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val savedState = rememberSaveableStateHolder()
    Box(modifier.fillMaxSize()) {
        if (!locked) savedState.SaveableStateProvider(APP_CONTENT_KEY, content)
        AnimatedVisibility(
            visible = locked,
            enter = EnterTransition.None,
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            lockScreen()
        }
    }
}

private const val APP_CONTENT_KEY = "app"
