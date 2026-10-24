package dev.kyralabs.pingogram.feature.profile.chatinfo.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatInfoActionRow
import dev.kyralabs.pingogram.feature.profile.chatinfo.ConfirmationDialog
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageBody
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageContent
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageScaffold
import dev.kyralabs.pingogram.feature.profile.chatinfo.PageError
import dev.kyralabs.pingogram.feature.profile.chatinfo.PageLoading
import dev.kyralabs.pingogram.feature.profile.chatinfo.RadioRow
import dev.kyralabs.pingogram.feature.profile.chatinfo.SavingIndicator
import dev.kyralabs.pingogram.feature.profile.chatinfo.SwitchRow
import dev.kyralabs.pingogram.feature.profile.ui.ProfileGroup
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRow
import dev.kyralabs.pingogram.feature.profile.ui.profileRowContainerColor
import kotlinx.coroutines.launch

/**
 * The settings page of a group or channel: hoists [ChatSettingsViewModel], shows refusals in snackbars, and opens the
 * supergroup a basic group became ([onOpenChat]).
 */
@Composable
internal fun ChatSettingsRoute(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatSettingsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showDetails)) }
                }

                is ChatSettingsEffect.Upgraded -> {
                    onOpenChat(effect.chatId)
                }
            }
        }
    }
    ChatSettingsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
    (state as? ChatSettingsUiState.Content)?.discussionPicker?.let { picker ->
        DiscussionPickerSheet(picker, viewModel::onEvent)
    }
}

/**
 * The settings of a group or channel (`docs/DESIGN_SYSTEM.md` §6, "Group and channel settings"), a settings-style
 * page of segmented groups without row icons, each where the user's role allows it: "Reactions" (radio rows, then
 * the emoji as toggle buttons while some are allowed), "Content" (restricting saving), "Posts" (a channel's
 * signatures), "Topics" (a supergroup's), "Discussion" (the linked chat) and "Group type" (upgrading a basic group).
 * Each change goes to Telegram at once, a wavy line showing until it answers; unlinking and upgrading ask first.
 */
@Composable
internal fun ChatSettingsScreen(
    state: ChatSettingsUiState,
    onEvent: (ChatSettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val isChannel = (state as? ChatSettingsUiState.Content)?.kind == GroupKind.Channel
    InfoPageScaffold(
        title = stringResource(if (isChannel) R.string.chat_settings_channel else R.string.chat_settings_group),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { innerPadding ->
        when (state) {
            ChatSettingsUiState.Loading -> {
                PageLoading(innerPadding)
            }

            is ChatSettingsUiState.Error -> {
                InfoPageBody(innerPadding) { PageError(state.error) { onEvent(ChatSettingsEvent.Retry) } }
            }

            is ChatSettingsUiState.Content -> {
                InfoPageContent(innerPadding) {
                    if (state.isSaving) SavingIndicator()
                    state.reactions?.let { ReactionsGroup(it, !state.isSaving, onEvent) }
                    SwitchGroups(state, onEvent)
                    state.discussion?.let { DiscussionGroupRows(it, isChannel, !state.isSaving, onEvent) }
                    if (state.canUpgrade) UpgradeGroup(!state.isSaving, onEvent)
                }
            }
        }
    }
    when ((state as? ChatSettingsUiState.Content)?.dialog) {
        ChatSettingsDialog.Unlink -> {
            ConfirmationDialog(
                title = stringResource(R.string.chat_settings_unlink_title),
                text = stringResource(R.string.chat_settings_unlink_text),
                confirm = stringResource(R.string.chat_settings_unlink_confirm),
                onConfirm = { onEvent(ChatSettingsEvent.ConfirmDialog) },
                onDismiss = { onEvent(ChatSettingsEvent.DismissDialog) },
            )
        }

        ChatSettingsDialog.Upgrade -> {
            ConfirmationDialog(
                title = stringResource(R.string.chat_settings_upgrade_title),
                text = stringResource(R.string.chat_settings_upgrade_text),
                confirm = stringResource(R.string.chat_settings_upgrade_confirm),
                onConfirm = { onEvent(ChatSettingsEvent.ConfirmDialog) },
                onDismiss = { onEvent(ChatSettingsEvent.DismissDialog) },
            )
        }

        null -> {}
    }
}

/** "Reactions": all, some or none as radio rows; while some, the emoji as toggle buttons in a segment below them. */
@Composable
private fun ReactionsGroup(
    setting: ReactionsSetting,
    enabled: Boolean,
    onEvent: (ChatSettingsEvent) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val choices =
        listOf(
            ReactionsChoice.All to R.string.chat_settings_reactions_all,
            ReactionsChoice.Some to R.string.chat_settings_reactions_some,
            ReactionsChoice.None to R.string.chat_settings_reactions_none,
        )
    val rows =
        choices.map { (choice, label) ->
            ProfileRow(choice.name) { shapes ->
                RadioRow(
                    label = stringResource(label),
                    selected = setting.choice == choice,
                    enabled = enabled,
                    shapes = shapes,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onEvent(ChatSettingsEvent.ChooseReactions(choice))
                    },
                )
            }
        }
    ProfileGroup(rows, Modifier.selectableGroup(), title = stringResource(R.string.chat_settings_reactions))
    if (setting.choice == ReactionsChoice.Some) ReactionPicker(setting, enabled, onEvent)
}

/** The emoji Telegram offers, each an `IconToggleButton` checked while members may react with it. */
@Composable
private fun ReactionPicker(
    setting: ReactionsSetting,
    enabled: Boolean,
    onEvent: (ChatSettingsEvent) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = profileRowContainerColor(),
    ) {
        FlowRow(
            modifier = Modifier.padding(MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            Text(
                text = stringResource(R.string.chat_settings_reactions_pick),
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.small),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            setting.available.forEach { emoji ->
                val description = stringResource(R.string.chat_settings_reaction, emoji)
                IconToggleButton(
                    checked = emoji in setting.chosen,
                    onCheckedChange = { onEvent(ChatSettingsEvent.ToggleReaction(emoji)) },
                    enabled = enabled,
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors =
                        IconButtonDefaults.iconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    modifier = Modifier.semantics { contentDescription = description },
                ) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

/** "Content", "Posts" and "Topics": switch rows, each sent at once. */
@Composable
private fun SwitchGroups(
    state: ChatSettingsUiState.Content,
    onEvent: (ChatSettingsEvent) -> Unit,
) {
    val enabled = !state.isSaving
    state.protectedContent?.let { protect ->
        val row =
            ProfileRow("protected") { shapes ->
                SwitchRow(
                    label = stringResource(R.string.chat_settings_protected),
                    checked = protect,
                    onCheckedChange = { onEvent(ChatSettingsEvent.SetProtectedContent(it)) },
                    shapes = shapes,
                    enabled = enabled,
                    supporting = stringResource(R.string.chat_settings_protected_summary),
                )
            }
        ProfileGroup(listOf(row), title = stringResource(R.string.chat_settings_content))
    }
    state.signMessages?.let { sign ->
        val rows =
            listOfNotNull(
                ProfileRow("sign") { shapes ->
                    SwitchRow(
                        label = stringResource(R.string.chat_settings_sign),
                        checked = sign.sign,
                        onCheckedChange = { onEvent(ChatSettingsEvent.SetSignMessages(it, sign.showProfiles)) },
                        shapes = shapes,
                        enabled = enabled,
                        supporting = stringResource(R.string.chat_settings_sign_summary),
                    )
                },
                ProfileRow("profiles") { shapes ->
                    SwitchRow(
                        label = stringResource(R.string.chat_settings_show_profiles),
                        checked = sign.showProfiles,
                        onCheckedChange = { onEvent(ChatSettingsEvent.SetSignMessages(true, it)) },
                        shapes = shapes,
                        enabled = enabled,
                        supporting = stringResource(R.string.chat_settings_show_profiles_summary),
                    )
                }.takeIf { sign.sign },
            )
        ProfileGroup(rows, title = stringResource(R.string.chat_settings_posts))
    }
    state.topics?.let { topics -> TopicsGroup(topics, enabled, onEvent) }
}

@Composable
private fun TopicsGroup(
    topics: TopicsSetting,
    enabled: Boolean,
    onEvent: (ChatSettingsEvent) -> Unit,
) {
    val rows =
        listOfNotNull(
            ProfileRow("topics") { shapes ->
                SwitchRow(
                    label = stringResource(R.string.chat_settings_topics_on),
                    checked = topics.enabled,
                    onCheckedChange = { onEvent(ChatSettingsEvent.SetTopics(it, topics.tabs)) },
                    shapes = shapes,
                    enabled = enabled,
                    supporting = stringResource(R.string.chat_settings_topics_summary),
                )
            },
            ProfileRow("tabs") { shapes ->
                SwitchRow(
                    label = stringResource(R.string.chat_settings_topic_tabs),
                    checked = topics.tabs,
                    onCheckedChange = { onEvent(ChatSettingsEvent.SetTopics(true, it)) },
                    shapes = shapes,
                    enabled = enabled,
                    supporting = stringResource(R.string.chat_settings_topic_tabs_summary),
                )
            }.takeIf { topics.enabled },
        )
    ProfileGroup(rows, title = stringResource(R.string.chat_settings_topics))
}

/**
 * "Discussion": a channel's discussion group, a row that opens the groups to pick from; or the channel a group
 * discusses; then "Unlink" in `error` while there is one.
 */
@Composable
private fun DiscussionGroupRows(
    discussion: DiscussionSetting,
    isChannel: Boolean,
    enabled: Boolean,
    onEvent: (ChatSettingsEvent) -> Unit,
) {
    val label =
        stringResource(
            if (isChannel) R.string.chat_settings_discussion_group else R.string.chat_settings_linked_channel,
        )
    val detail = discussion.title ?: stringResource(R.string.chat_settings_discussion_none)
    val rows =
        listOfNotNull(
            ProfileRow("linked") { shapes ->
                ChatInfoActionRow(
                    text = label,
                    shapes = shapes,
                    onClick = { onEvent(ChatSettingsEvent.OpenDiscussionPicker) },
                    detail = detail,
                    enabled = enabled && discussion.canChange,
                )
            },
            ProfileRow("unlink") { shapes ->
                ChatInfoActionRow(
                    text = stringResource(R.string.chat_settings_unlink),
                    shapes = shapes,
                    onClick = { onEvent(ChatSettingsEvent.Unlink) },
                    enabled = enabled,
                    contentColor = MaterialTheme.colorScheme.error,
                )
            }.takeIf { discussion.chatId != 0L && discussion.canChange },
        )
    ProfileGroup(rows, title = stringResource(R.string.chat_settings_discussion))
}

@Composable
private fun UpgradeGroup(
    enabled: Boolean,
    onEvent: (ChatSettingsEvent) -> Unit,
) {
    val row =
        ProfileRow("upgrade") { shapes ->
            ChatInfoActionRow(
                text = stringResource(R.string.chat_settings_upgrade),
                shapes = shapes,
                onClick = { onEvent(ChatSettingsEvent.Upgrade) },
                detail = stringResource(R.string.chat_settings_upgrade_summary),
                enabled = enabled,
            )
        }
    ProfileGroup(listOf(row), title = stringResource(R.string.chat_settings_upgrade_section))
}
