package dev.kyralabs.pingogram.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.kyralabs.pingogram.core.navigation.NewChatRoute
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chats.navigation.newChatButtonTransform
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import dev.kyralabs.pingogram.core.ui.R as UiR
import dev.kyralabs.pingogram.feature.chats.R as ChatsR
import dev.kyralabs.pingogram.feature.contacts.R as ContactsR

/**
 * The Chats tab's "New chat" beside the floating navigation bar on compact windows (`REQUIREMENTS.md` FR-L7,
 * `docs/DESIGN_SYSTEM.md` §13.2, §6.1): a 64 dp rounded square (`sizes.experimentalFab`, the shape scale's
 * `largeIncreased`) with the filled edit symbol, on `tertiaryContainer` / `onTertiaryContainer`, named "New chat" for
 * screen readers. It shows while [shown]
 * (on the Chats tab, until New chat opens) and grows into New chat ([NewChatButtonSlot]); [onClick] opens it.
 */
@Composable
internal fun BarNewChatButton(
    shown: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.largeIncreased
    val colors = MaterialTheme.colorScheme
    val size = MaterialTheme.sizes.experimentalFab
    NewChatButtonSlot(shown = shown, shape = shape, modifier = modifier.size(size)) { transform ->
        FloatingToolbarDefaults.StandardFloatingActionButton(
            onClick = onClick,
            modifier = transform.size(size),
            shape = shape,
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_edit),
                contentDescription = stringResource(ChatsR.string.new_chat),
            )
        }
    }
}

/**
 * The rail's primary action on the Chats tab: "New chat", in search's old place (owner decisions 2026-11-06 and
 * 2026-11-07). The collapsed rail shows a round FAB, the expanded one an extended FAB with its label in a full pill
 * (`docs/DESIGN_SYSTEM.md` §6), both in `primaryContainer`. The rail's header places it at its start edge, so it
 * brings its own start padding, as in Material's rail samples. It grows into New chat and back ([NewChatButtonSlot]),
 * and is away while not [shown].
 */
@Composable
internal fun RailNewChatButton(
    navigationSuiteType: NavigationSuiteType,
    shown: Boolean,
    onClick: () -> Unit,
) {
    val expanded = navigationSuiteType == NavigationSuiteType.WideNavigationRailExpanded
    val icon = painterResource(UiR.drawable.ic_edit)
    val label = stringResource(ChatsR.string.new_chat)
    val shape = CircleShape
    NewChatButtonSlot(
        shown = shown,
        shape = shape,
        // The extended and the collapsed button differ in size: each keeps its own place.
        sizeKey = expanded,
        modifier = Modifier.padding(start = RAIL_HEADER_START_PADDING),
    ) { transform ->
        if (expanded) {
            ExtendedFloatingActionButton(
                onClick = onClick,
                icon = { Icon(painter = icon, contentDescription = null) },
                text = { Text(label) },
                modifier = transform,
                shape = shape,
            )
        } else {
            FloatingActionButton(onClick = onClick, modifier = transform, shape = shape) {
                Icon(painter = icon, contentDescription = label)
            }
        }
    }
}

/**
 * What sits beside the floating navigation bar for [tab]: "New chat" on the Chats tab while New chat is not open
 * ([newChatOpen]), and "Add contact" on the Contacts tab (`docs/DESIGN_SYSTEM.md` §13.8).
 */
@Composable
internal fun BarPrimaryActions(
    tab: TopLevelDestination?,
    newChatOpen: Boolean,
    onNewChat: () -> Unit,
    onNewContact: () -> Unit,
) {
    Box {
        BarNewChatButton(shown = tab == TopLevelDestination.Chats && !newChatOpen, onClick = onNewChat)
        BarAddContactButton(shown = tab == TopLevelDestination.Contacts, onClick = onNewContact)
    }
}

/** The rail header's counterpart of [BarPrimaryActions]. */
@Composable
internal fun RailPrimaryActions(
    navigationSuiteType: NavigationSuiteType,
    tab: TopLevelDestination?,
    newChatOpen: Boolean,
    onNewChat: () -> Unit,
    onNewContact: () -> Unit,
) {
    Box {
        RailNewChatButton(
            navigationSuiteType = navigationSuiteType,
            shown = tab == TopLevelDestination.Chats && !newChatOpen,
            onClick = onNewChat,
        )
        RailAddContactButton(
            navigationSuiteType = navigationSuiteType,
            shown = tab == TopLevelDestination.Contacts,
            onClick = onNewContact,
        )
    }
}

/**
 * The Contacts tab's "Add contact" (`docs/DESIGN_SYSTEM.md` §13.8; owner decision to
 * confirm, 2026-11-07), where "New chat" sits on the Chats tab: beside the floating navigation bar, the same 64 dp
 * rounded square on `tertiaryContainer` with the add-person symbol. It shows while [shown] (on the Contacts tab) and
 * fades in and out; [onClick] opens Add contact.
 */
@Composable
internal fun BarAddContactButton(
    shown: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val size = MaterialTheme.sizes.experimentalFab
    val shape = MaterialTheme.shapes.largeIncreased
    FadingButton(shown, modifier.size(size)) {
        FloatingToolbarDefaults.StandardFloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(size),
            shape = shape,
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_person_add),
                contentDescription = stringResource(ContactsR.string.contacts_add),
            )
        }
    }
}

/**
 * The rail's "Add contact" on the Contacts tab, as [RailNewChatButton] on the Chats tab: a
 * round FAB on the collapsed rail, an extended one with its label on the expanded rail. It fades in and out with
 * [shown].
 */
@Composable
internal fun RailAddContactButton(
    navigationSuiteType: NavigationSuiteType,
    shown: Boolean,
    onClick: () -> Unit,
) {
    val icon = painterResource(UiR.drawable.ic_person_add)
    val label = stringResource(ContactsR.string.contacts_add)
    FadingButton(shown, Modifier.padding(start = RAIL_HEADER_START_PADDING)) {
        if (navigationSuiteType == NavigationSuiteType.WideNavigationRailExpanded) {
            ExtendedFloatingActionButton(
                onClick = onClick,
                icon = { Icon(painter = icon, contentDescription = null) },
                text = { Text(label) },
                shape = CircleShape,
            )
        } else {
            FloatingActionButton(onClick = onClick, shape = CircleShape) {
                Icon(painter = icon, contentDescription = label)
            }
        }
    }
}

/** [button] while [shown], fading in and out on the effects springs. */
@Composable
private fun FadingButton(
    shown: Boolean,
    modifier: Modifier = Modifier,
    button: @Composable () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(motionScheme.fastEffectsSpec()),
        exit = fadeOut(motionScheme.fastEffectsSpec()),
    ) {
        button()
    }
}

/** Centers a 56 dp FAB over the items of the 96 dp wide collapsed rail; Material has no token for it. */
private val RAIL_HEADER_START_PADDING = 20.dp

/**
 * Where the "New chat" button sits, beside the floating navigation bar or in the rail's header (`docs/DESIGN_SYSTEM.md`
 * §5, §6): the [button] while [shown], given the modifier of its container transform into New chat
 * ([newChatButtonTransform], in [shape]). The shell hides it while New chat shows, and the button and the screen share
 * their bounds as one comes and the other goes, on the motion scheme's springs, each fading on the effects springs.
 * Without the transform (reduced motion) it fades in and out; it fades as the user switches to another tab too.
 *
 * Hidden, the slot keeps the size the button had (per [sizeKey]), so the bar or the rail around it never moves.
 *
 * Only a slot that was placed takes part in the transform and in the semantics tree: `NavigationSuiteScaffold`
 * composes its primary action a second time for navigation bars and never places that copy beside a rail, and a
 * second, unplaced side of the same transform would have no bounds to grow from.
 */
@Composable
private fun NewChatButtonSlot(
    shown: Boolean,
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    sizeKey: Any? = null,
    button: @Composable (transform: Modifier) -> Unit,
) {
    val keptSize = remember(sizeKey) { KeptSize() }
    var placed by remember { mutableStateOf(false) }
    val motionScheme = MaterialTheme.motionScheme
    Box(
        modifier
            .keepingSize(keptSize)
            .onPlaced { placed = true }
            .then(if (placed) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(motionScheme.fastEffectsSpec()),
            exit = fadeOut(motionScheme.fastEffectsSpec()),
        ) {
            ProvideNavAnimatedVisibilityScope(this) {
                button(if (placed) Modifier.newChatButtonTransform(shape) else Modifier)
            }
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

/**
 * Whether New chat shows, anywhere on the back stack, also below a group or channel it is creating: the "New chat"
 * button is away meanwhile, so only New chat's own coming and going moves it. Derived, so that only opening or leaving
 * New chat recomposes the shell.
 */
@Composable
internal fun rememberNewChatOpen(navController: NavController): State<Boolean> {
    val currentEntry = navController.currentBackStackEntryAsState()
    return remember(navController, currentEntry) {
        derivedStateOf {
            // Read for every change of the destination, which is when the back stack changes.
            currentEntry.value
            runCatching { navController.getBackStackEntry<NewChatRoute>() }.isSuccess
        }
    }
}
