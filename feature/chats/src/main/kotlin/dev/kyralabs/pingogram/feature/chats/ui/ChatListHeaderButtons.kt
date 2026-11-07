package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.transition.LocalNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.navigation.ChatListHeaderActions
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.feature.chats.navigation.searchButtonTransform
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The header's search button (`docs/DESIGN_SYSTEM.md` §6 "Chat list top bar", "Global search"; owner decision
 * 2026-11-06): a [TopBarAction] with the search symbol, which opens global search ([ChatListHeaderActions.openSearch])
 * and grows into it ([SearchButtonSlot]). Without [actions] (previews, tests) it does nothing.
 */
@Composable
internal fun HeaderSearchButton(actions: ChatListHeaderActions?) {
    val label = stringResource(R.string.chat_list_search)
    val icon: @Composable () -> Unit = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) }
    if (actions == null) {
        TopBarAction(label = label, onClick = {}, icon = icon)
    } else {
        SearchButtonSlot(shown = !actions.isSearchOpen, search = actions.search, shape = CircleShape) { transform ->
            TopBarAction(label = label, onClick = actions.openSearch, modifier = transform, icon = icon)
        }
    }
}

/**
 * The signed-in user's avatar at the header's end ([summary]'s account, read here alone, so the counts changing
 * recompose nothing of it; the person symbol until TDLib named them), an icon
 * button named "Your profile" with its tooltip, which opens the Settings tab ([ChatListHeaderActions.openAccount]).
 * A long press opens the account menu where the app offers one ([ChatListHeaderActions.accountMenu]), as the Settings
 * tab's long press does. The avatar is decorative: the button says what it does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeaderAccountButton(
    summary: () -> ChatListSummary,
    actions: ChatListHeaderActions?,
) {
    val account by remember(summary) { derivedStateOf { summary().account } }
    val label = stringResource(R.string.chat_list_your_profile)
    val menu = actions?.accountMenu ?: { content -> content(Modifier) }
    menu { menuModifier ->
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = { actions?.openAccount?.invoke() },
                modifier = menuModifier.semantics { contentDescription = label },
                shapes = IconButtonDefaults.shapes(),
            ) {
                val shown = account
                if (shown != null) {
                    Avatar(
                        photo = shown.photo,
                        name = shown.name.resolve(),
                        id = shown.id,
                        size = MaterialTheme.sizes.avatarExtraSmall,
                    )
                } else {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_person_filled),
                        contentDescription = null,
                        modifier = Modifier.size(MaterialTheme.sizes.avatarExtraSmall),
                    )
                }
            }
        }
    }
}

/**
 * Where the search button sits (`docs/DESIGN_SYSTEM.md` §5, §6): the [button] while [shown], given the modifier of its
 * container transform into search ([searchButtonTransform], in [shape]). While search shows the button is away, and
 * the two share their bounds as one comes and the other goes: the button grows into search and search shrinks back
 * into it, on the motion scheme's springs, each fading on the effects springs. Without the transform (reduced motion)
 * it fades in and out.
 *
 * The chat list composes anew as search leaves (a pushed destination's pop, docked search's crossfade): a button
 * composed while search is still composed ([GlobalSearchState.searchesComposed]) or while its screen comes in comes in
 * too, so it grows out of search; otherwise it starts where it is. Hidden, the slot keeps the size the button had, so
 * the actions around it never move.
 */
@Composable
internal fun SearchButtonSlot(
    shown: Boolean,
    search: GlobalSearchState,
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    button: @Composable (transform: Modifier) -> Unit,
) {
    val keptSize = remember { KeptSize() }
    val screen = LocalNavAnimatedVisibilityScope.current?.transition
    val visibility =
        remember {
            val entering = screen != null && screen.currentState != EnterExitState.Visible
            MutableTransitionState(shown && search.searchesComposed == 0 && !entering)
        }
    visibility.targetState = shown
    val motionScheme = MaterialTheme.motionScheme
    Box(modifier.keepingSize(keptSize)) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(motionScheme.fastEffectsSpec()),
            exit = fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            ProvideNavAnimatedVisibilityScope(this) { button(Modifier.searchButtonTransform(search, shape)) }
        }
    }
}

/** The largest size content laid out with [keepingSize] had; a plain holder, written and read in the layout phase. */
private class KeptSize {
    var size = IntSize.Zero
}

/** Lays this content out at least as large as it ever was ([kept]), so it keeps its place while it is gone. */
private fun Modifier.keepingSize(kept: KeptSize): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        kept.size = IntSize(maxOf(kept.size.width, placeable.width), maxOf(kept.size.height, placeable.height))
        val width = kept.size.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = kept.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) { placeable.place(0, 0) }
    }
