package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.component.plusBottom
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.transition.FullBleedShape
import dev.kyralabs.pingogram.core.ui.transition.LocalNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.core.ui.transition.containerTransform
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.coroutines.flow.first
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Chats tab's primary action (`REQUIREMENTS.md` FR-L7, `docs/DESIGN_SYSTEM.md` §6): one
 * [SmallExtendedFloatingActionButton] "New chat" in `primaryContainer`, 56 dp tall (the small extended FAB's height)
 * with its label in `labelLarge`, which collapses to the 56 dp FAB while the list scrolls towards its end and expands
 * again as it scrolls back ([state]). It shows and hides ([visible]) with Material's FAB animation, scaling from its
 * bottom end (`animateFloatingActionButton`), and grows into the New chat screen it opens ([onClick]): a container
 * transform from its corners to the screen's square ones ([NewChatTransformKey]), and back. Screen readers hear "New
 * chat" whether or not the label shows. Hidden, it is only scaled down and faded (Material's animation leaves it
 * placed), so it then takes no taps or focus, and screen readers do not find it ([HiddenButton]).
 *
 * The transform is armed only from a tap until the chat list is back and settled ([NewChatButtonState.transforming]):
 * shared bounds add their animations to the destination's transition, and would change how every other transition of
 * the chat list plays, e.g. how a predictive back gesture from a conversation seeks it.
 *
 * Only this composable reads whether it is expanded, so scrolling recomposes it, once, when it collapses or expands,
 * and nothing else (`docs/PERFORMANCE.md` §4). It floats over the chat list's `Scaffold` rather than in its button
 * slot: there, every frame of its width animation measured the whole `Scaffold` again.
 */
@Composable
internal fun NewChatButton(
    state: NewChatButtonState,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.new_chat)
    val shape = MaterialTheme.shapes.large
    SmallExtendedFloatingActionButton(
        text = {
            Text(
                text = label,
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.labelLarge,
            )
        },
        icon = { Icon(painter = painterResource(UiR.drawable.ic_edit), contentDescription = null) },
        onClick = {
            if (visible) {
                state.transforming = true
                onClick()
            }
        },
        modifier =
            modifier
                .animateFloatingActionButton(visible = visible, alignment = Alignment.BottomEnd)
                .containerTransform(
                    NewChatTransformKey,
                    shape = shape,
                    counterpartShape = FullBleedShape,
                    enabled = state.transforming,
                ).then(if (visible) Modifier.semantics { contentDescription = label } else HiddenButton),
        expanded = state.expanded,
        shape = shape,
    )
    DisarmTransformWhenBack(state)
}

/** What the button keeps while hidden: nothing screen readers or the keyboard reach. */
private val HiddenButton = Modifier.clearAndSetSemantics {}.focusProperties { canFocus = false }

/**
 * Disarms the container transform of [state] once the destination's transition has run and left the chat list shown:
 * back from New chat. Leaving for New chat disposes the button first, and its saved state keeps the transform armed
 * for the way back. Nothing to do outside the `NavHost` (previews, tests). Part of the button's composition, so it
 * adds no recomposition of its own.
 */
@Composable
@NonRestartableComposable
private fun DisarmTransformWhenBack(state: NewChatButtonState) {
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return
    if (!state.transforming) return
    LaunchedEffect(visibility, state) {
        val transition = visibility.transition
        snapshotFlow { transition.isRunning }.first { it }
        snapshotFlow { !transition.isRunning && transition.currentState == EnterExitState.Visible }.first { it }
        state.transforming = false
    }
}

/**
 * Whether the "New chat" button shows its label ([NewChatButton]): it collapses while the user scrolls the list
 * towards its end and expands as they scroll back, followed through [nestedScrollConnection] on an ancestor of the
 * list. The connection consumes nothing, and writes [expanded] only when the direction changes, so it never recomposes
 * anything while the list scrolls on in one direction.
 */
@Stable
internal class NewChatButtonState(
    initiallyExpanded: Boolean = true,
    initiallyTransforming: Boolean = false,
) {
    var expanded by mutableStateOf(initiallyExpanded)
        private set

    /** Whether the button grows into the New chat screen, and back: from a tap until the chat list is back. */
    var transforming by mutableStateOf(initiallyTransforming)

    val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Negative while the content moves up, towards the list's end.
                when {
                    available.y < 0f -> expanded = false
                    available.y > 0f -> expanded = true
                }
                return Offset.Zero
            }
        }

    companion object {
        /**
         * Keeps whether the button is expanded and transforming across a rotation, process death, and a screen pushed
         * above.
         */
        val Saver: Saver<NewChatButtonState, Any> =
            listSaver(
                save = { listOf(it.expanded, it.transforming) },
                restore = { NewChatButtonState(initiallyExpanded = it[0], initiallyTransforming = it[1]) },
            )
    }
}

/** A [NewChatButtonState], saved. */
@Composable
internal fun rememberNewChatButtonState(): NewChatButtonState =
    rememberSaveable(saver = NewChatButtonState.Saver) { NewChatButtonState() }

/** What the "New chat" button and the New chat screen share for their container transform. */
internal data object NewChatTransformKey

@PingogramPreviews
@Composable
private fun NewChatButtonPreview() {
    PingogramPreview {
        NewChatButton(state = NewChatButtonState(), visible = true, onClick = {})
    }
}

@PingogramPreviews
@Composable
private fun NewChatButtonCollapsedPreview() {
    PingogramPreview {
        NewChatButton(state = NewChatButtonState(initiallyExpanded = false), visible = true, onClick = {})
    }
}

/**
 * [padding] with room at the bottom for the Chats tab's "New chat" button, so the list's end rests clear of it: the
 * button, the 16 dp below it, the same above. Kept in selection mode, where the button hides, so the list
 * does not move; none in the archive ([kind]).
 */
@Composable
internal fun rememberClearOfNewChatButton(
    padding: PaddingValues,
    kind: ChatListKind,
): PaddingValues {
    val space =
        if (kind ==
            ChatListKind.Chats
        ) {
            MaterialTheme.sizes.fabSmall + MaterialTheme.spacing.large * 2
        } else {
            0.dp
        }
    return remember(padding, space) { padding.plusBottom(space) }
}
