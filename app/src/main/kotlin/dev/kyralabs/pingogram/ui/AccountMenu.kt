package dev.kyralabs.pingogram.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.account.AccountMenuItem
import dev.kyralabs.pingogram.account.AccountMenuSource
import dev.kyralabs.pingogram.account.AccountMenuState
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Settings tab's account menu (`docs/adr/0031-multiple-accounts.md`, `docs/DESIGN_SYSTEM.md` §6 "Account
 * switcher"), as Telegram's iOS app switches accounts, since Pingogram has no side menu: a long press on the tab
 * [content] (the floating navigation bar's or the rail's) opens an expressive [DropdownMenuPopup] anchored to it, the
 * accounts in one group (avatar, name, the shown one checked, the others' unread chats in a `primary` [Badge]) and
 * "Add account" in a second while [AccountMenuState.canAdd]. A tap on another account switches to it ([onSwitch]),
 * after which the app opens again on it; on the shown one it only closes the menu.
 *
 * [content] gets the modifier to put on the tab: the long press (with the `LongPress` haptic), which leaves the tab's
 * own tap as it is, and the same choices as screen readers' custom actions on the tab ("Switch to Grace Hopper", "Add
 * account"). Without a [menu] (one account, or the app offering one only), the tab is left as it is.
 */
@Composable
internal fun AccountMenuAnchor(
    menu: AccountMenuState?,
    onSwitch: (slot: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (menu == null) {
        Box(modifier) { content(Modifier) }
        return
    }
    val expanded = rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        content(accountMenuTrigger(menu, expanded, onSwitch, onAdd))
        AccountMenuPopup(menu, expanded, onSwitch, onAdd)
    }
}

/**
 * The account menu's trigger on a tab: a long press (with the `LongPress` haptic) opens it ([expanded]), and screen
 * readers get its choices as custom actions. For elements that cannot be wrapped in [AccountMenuAnchor] without
 * changing their layout (the rail's items), with [AccountMenuPopup] inside them.
 */
@Composable
internal fun accountMenuTrigger(
    menu: AccountMenuState,
    expanded: MutableState<Boolean>,
    onSwitch: (slot: Int) -> Unit,
    onAdd: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val resources = LocalResources.current
    val currentMenu by rememberUpdatedState(menu)
    val currentOnSwitch by rememberUpdatedState(onSwitch)
    val currentOnAdd by rememberUpdatedState(onAdd)
    return Modifier
        .onLongPress {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            expanded.value = true
        }.semantics {
            customActions =
                currentMenu.accounts.filterNot { it.isActive }.map { account ->
                    CustomAccessibilityAction(
                        resources.getString(R.string.account_menu_switch, account.name.bidiIsolated()),
                    ) {
                        currentOnSwitch(account.slot)
                        true
                    }
                } +
                listOfNotNull(
                    if (currentMenu.canAdd) {
                        CustomAccessibilityAction(resources.getString(R.string.account_menu_add)) {
                            currentOnAdd()
                            true
                        }
                    } else {
                        null
                    },
                )
        }
}

/** The account menu's popup while [expanded], anchored to the layout it is composed in. */
@Composable
internal fun AccountMenuPopup(
    menu: AccountMenuState,
    expanded: MutableState<Boolean>,
    onSwitch: (slot: Int) -> Unit,
    onAdd: () -> Unit,
) {
    DropdownMenuPopup(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
        AccountMenuGroups(
            menu = menu,
            onSwitch = { slot ->
                expanded.value = false
                onSwitch(slot)
            },
            onAdd = {
                expanded.value = false
                onAdd()
            },
            onDismiss = { expanded.value = false },
        )
    }
}

/**
 * What the app shell hands the navigation's Settings tab: the menu's [state] and what its choices do; one value, so
 * the bar and the rail take it as they take their other parameters.
 */
@Immutable
internal class AccountMenuActions(
    val state: AccountMenuState,
    val onSwitch: (slot: Int) -> Unit,
    val onAdd: () -> Unit,
)

/** The Settings tab's account menu from [accounts], while it offers one (`docs/adr/0031-multiple-accounts.md`). */
@Composable
internal fun rememberAccountMenu(accounts: AccountMenuSource?): AccountMenuActions? {
    if (accounts == null) return null
    val state by accounts.state.collectAsStateWithLifecycle()
    return remember(accounts, state) {
        state?.let { AccountMenuActions(it, onSwitch = accounts::switchTo, onAdd = accounts::addAccount) }
    }
}

/** The menu's groups: the accounts, then "Add account" on its own. */
@Composable
private fun AccountMenuGroups(
    menu: AccountMenuState,
    onSwitch: (slot: Int) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val groups = if (menu.canAdd) 2 else 1
    DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = groups)) {
        menu.accounts.forEachIndexed { index, account ->
            AccountMenuEntry(
                account = account,
                shape = MenuDefaults.itemShape(index, menu.accounts.size).shape,
                onClick = { if (account.isActive) onDismiss() else onSwitch(account.slot) },
            )
        }
    }
    if (menu.canAdd) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = groups)) {
            DropdownMenuItem(
                onClick = onAdd,
                text = { Text(stringResource(R.string.account_menu_add)) },
                shape = MenuDefaults.itemShape(0, 1).shape,
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_person_add), contentDescription = null) },
            )
        }
    }
}

/** One account: its avatar, its name as user content, a check on the shown one, the others' unread chats. */
@Composable
private fun AccountMenuEntry(
    account: AccountMenuItem,
    shape: Shape,
    onClick: () -> Unit,
) {
    val current = stringResource(R.string.account_menu_current)
    val unread =
        pluralStringResource(R.plurals.account_menu_unread_chats, account.unreadChats, account.unreadChats)
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(account.name, style = LocalTextStyle.current.userContent, maxLines = 1) },
        shape = shape,
        leadingIcon = {
            Avatar(
                photo = account.photo,
                name = account.name,
                id = account.userId,
                size = MaterialTheme.sizes.avatarExtraSmall,
            )
        },
        trailingContent =
            when {
                account.isActive -> {
                    { Icon(painterResource(UiR.drawable.ic_check), contentDescription = current) }
                }

                account.unreadChats > 0 -> {
                    {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = unread },
                        ) {
                            Text(account.unreadChats.toString(), style = LocalTextStyle.current.numericContent)
                        }
                    }
                }

                else -> {
                    null
                }
            },
    )
}

/**
 * Calls [onLongPress] once a pointer stayed down without moving past the touch slop for the long press timeout. It
 * watches in the initial pass and consumes nothing until then, so the element's own tap and scrolling keep working;
 * after a long press it consumes the rest of the gesture, so the element's tap does not follow it.
 */
private fun Modifier.onLongPress(onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val ended =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    awaitReleaseOrMove(down.id, down.position, viewConfiguration.touchSlop)
                }
            if (ended != null) return@awaitEachGesture
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

/** Returns once pointer [id] is up, gone, or more than [slop] from [start]. */
private suspend fun AwaitPointerEventScope.awaitReleaseOrMove(
    id: PointerId,
    start: Offset,
    slop: Float,
) {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed || (change.position - start).getDistance() > slop) return
    }
}

@PingogramPreviews
@Composable
private fun AccountMenuGroupsPreview() {
    PingogramPreview {
        Column {
            AccountMenuGroups(PreviewMenu, onSwitch = {}, onAdd = {}, onDismiss = {})
        }
    }
}

/** Three accounts, the shown one first, one with unread chats; below the limit for the preview's sake. */
internal val PreviewMenu =
    AccountMenuState(
        accounts =
            persistentListOf(
                AccountMenuItem(
                    slot = 0,
                    userId = 42,
                    name = "Ada Lovelace",
                    photo = null,
                    unreadChats = 2,
                    isActive = true,
                ),
                AccountMenuItem(
                    slot = 1,
                    userId = 43,
                    name = "Grace Hopper",
                    photo = null,
                    unreadChats = 3,
                    isActive = false,
                ),
                AccountMenuItem(
                    slot = 2,
                    userId = 44,
                    name = "Alan Turing",
                    photo = null,
                    unreadChats = 0,
                    isActive = false,
                ),
            ),
        canAdd = true,
    )
