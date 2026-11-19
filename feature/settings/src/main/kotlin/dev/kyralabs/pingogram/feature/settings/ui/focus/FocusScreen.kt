package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.ui.component.CategoryIconBadge
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.FocusBadge
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.component.symbol
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.CategoryColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.settingsGroupIconColors
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The Focus page: hoists [FocusViewModel], shows why a change was not saved in a snackbar. */
@Composable
internal fun FocusRoute(
    onOpenProfile: (id: Long, template: FocusTemplate?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FocusEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    FocusScreen(state, viewModel::onEvent, onOpenProfile, onBack, modifier, snackbarHostState)
}

/**
 * Settings → Focus (`docs/DESIGN_SYSTEM.md` §13.13, board 31): the profiles as a connected list, each its symbol in a
 * circle, its name, how it runs and what it lets through, and a switch that turns it on or off by hand; the profile on
 * now on `primaryContainer` with "On now". Then "New profile". Without profiles, three shapes, "No Focus profiles yet"
 * and the templates, each opening the editor filled in. Last, "While Focus is on": whether the chat list's header shows
 * the profile on now, and a disabled row: Telegram has no status that tells people the user is focusing. Nothing shows
 * until the profiles are read, a matter of milliseconds.
 */
@Composable
internal fun FocusScreen(
    state: FocusUiState,
    onEvent: (FocusEvent) -> Unit,
    onOpenProfile: (id: Long, template: FocusTemplate?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_focus),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        val profiles = state.profiles ?: return@SettingsPageScaffold
        if (profiles.isEmpty()) {
            FocusEmpty(onOpenProfile)
        } else {
            SettingsGroup(
                title = stringResource(R.string.settings_focus_profiles),
                footer = stringResource(R.string.settings_focus_profiles_footer),
            ) {
                profiles.forEach { profile ->
                    item(key = profile.id) { shapes ->
                        FocusProfileRow(
                            item = profile,
                            shapes = shapes,
                            onOpen = { onOpenProfile(profile.id, null) },
                            onToggle = { onEvent(FocusEvent.Toggle(profile.id, it)) },
                        )
                    }
                }
                item(key = NEW_PROFILE) { shapes ->
                    SettingsRow(
                        title = stringResource(R.string.settings_focus_new_profile),
                        shapes = shapes,
                        icon = UiR.drawable.ic_add,
                        onClick = { onOpenProfile(0, FocusTemplate.Blank) },
                        opensPage = true,
                    )
                }
            }
        }
        WhileOnGroup(
            showsInChatList = state.showsInChatList,
            onShowsInChatListChange = { onEvent(FocusEvent.ShowInChatList(it)) },
        )
    }
}

/**
 * A profile's row: its symbol in a circle (the group's category color; inverted on the profile on now), its name,
 * "On now" while it is on, how it runs and what it lets through; a tap opens the editor, the switch at its end turns it
 * on or off by hand. On `primaryContainer` while it is on. Screen readers hear the row as one item, with the switch
 * apart.
 */
@Composable
private fun FocusProfileRow(
    item: FocusProfileItem,
    shapes: ListItemShapes,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val rowColors =
        if (item.isOn) {
            settingsRowColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                supportingContentColor = colors.onPrimaryContainer,
            )
        } else {
            settingsRowColors()
        }
    val how = item.schedule?.let { scheduleText(it) } ?: stringResource(R.string.settings_focus_by_hand)
    val allows = allowsText(item)
    SettingsListItem(
        onClick = onOpen,
        shapes = shapes,
        colors = rowColors,
        modifier = Modifier.cardSemantics(item.isOn) { toggle(haptics, !item.isOn, onToggle) },
        leadingContent = {
            CategoryIconBadge(
                icon = painterResource(item.icon.symbol()),
                colors =
                    if (item.isOn) {
                        CategoryColors(colors.onPrimaryContainer, colors.primaryContainer)
                    } else {
                        settingsGroupIconColors()
                    },
            )
        },
        supportingContent = {
            Column {
                Text(how)
                Text(allows)
            }
        },
        trailingContent = {
            CheckSwitch(
                checked = item.isOn,
                onCheckedChange = { checked -> toggle(haptics, checked, onToggle) },
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
    ) {
        ProfileTitle(item.name, item.isOn)
    }
}

private fun toggle(
    haptics: HapticFeedback,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    haptics.performToggle(checked)
    onToggle(checked)
}

/** Without profiles: three shapes, "No Focus profiles yet", then the templates. */
@Composable
private fun FocusEmpty(onOpenProfile: (id: Long, template: FocusTemplate?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    SettingsGroup {
        item { shapes ->
            SettingsSegment(shapes) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    EmptyShapes()
                    Text(
                        stringResource(R.string.settings_focus_empty_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.settings_focus_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    FocusTemplatesGroup(onOpenProfile)
}

/** "Start with": the templates, each opening the editor filled in. */
@Composable
private fun FocusTemplatesGroup(onOpenProfile: (id: Long, template: FocusTemplate?) -> Unit) {
    SettingsGroup(title = stringResource(R.string.settings_focus_templates)) {
        FocusTemplate.entries.forEach { template ->
            item(key = template) { shapes ->
                SettingsRow(
                    title = stringResource(template.title()),
                    shapes = shapes,
                    // Each template's symbol, as its profile shows it (board 31f), in the group's circle.
                    icon = template.icon()?.symbol() ?: UiR.drawable.ic_add,
                    supportingContent = { Text(stringResource(template.summary())) },
                    onClick = { onOpenProfile(0, template) },
                    opensPage = true,
                )
            }
        }
    }
}

/**
 * "While Focus is on": whether the chat list's header shows the profile on now, as a switch row, then a disabled row:
 * Telegram has no status that tells people the user is focusing.
 */
@Composable
private fun WhileOnGroup(
    showsInChatList: Boolean,
    onShowsInChatListChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    SettingsGroup(title = stringResource(R.string.settings_focus_while_on)) {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_focus_in_chat_list),
                shapes = shapes,
                icon = UiR.drawable.ic_chat_filled,
                supportingContent = { Text(stringResource(R.string.settings_focus_in_chat_list_summary)) },
                trailingContent = { CheckSwitch(checked = showsInChatList) },
                onClick = {
                    haptics.performToggle(!showsInChatList)
                    onShowsInChatListChange(!showsInChatList)
                },
                modifier =
                    Modifier.semantics {
                        role = Role.Switch
                        toggleableState = ToggleableState(showsInChatList)
                    },
            )
        }
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_focus_status),
                shapes = shapes,
                icon = UiR.drawable.ic_public,
                supportingContent = { Text(stringResource(R.string.settings_focus_status_summary)) },
                enabled = false,
            )
        }
    }
}

private const val NEW_PROFILE = "new"

/** From this font scale on, the empty page's shapes are smaller. */
private const val LARGE_FONT_SCALE = 1.5f

/** Three profile shapes overlapping, decorative. */
@Composable
private fun EmptyShapes() {
    val colors = MaterialTheme.colorScheme
    // Smaller from large font scales, so the title and the templates keep the room (board 31).
    val size =
        if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
            MaterialTheme.sizes.leadingIconContainer
        } else {
            MaterialTheme.sizes.avatarMedium
        }
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(-MaterialTheme.spacing.medium),
    ) {
        FocusBadge(FocusIcon.Work, size, colors.primary, colors.onPrimary)
        FocusBadge(FocusIcon.Sleep, size, colors.tertiary, colors.onTertiary)
        FocusBadge(FocusIcon.Weekend, size, colors.secondary, colors.onSecondary)
    }
}
