package dev.kyralabs.pingogram.feature.profile.chatinfo.settings

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The states of a group's or channel's settings page, shared by previews and screenshot tests. */
internal object ChatSettingsPreviewData {
    private val emoji = persistentListOf("👍", "❤", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🎉", "🤩", "🙏")

    val channel =
        ChatSettingsUiState.Content(
            kind = GroupKind.Channel,
            reactions = ReactionsSetting(ReactionsChoice.Some, persistentSetOf("👍", "❤", "🔥", "🎉"), emoji),
            protectedContent = false,
            signMessages = SignMessagesSetting(sign = true, showProfiles = false),
            topics = null,
            discussion = DiscussionSetting(chatId = 77, title = "Rust Berlin talk", canChange = true),
            canUpgrade = false,
        )

    val supergroup =
        ChatSettingsUiState.Content(
            kind = GroupKind.Supergroup,
            reactions = ReactionsSetting(ReactionsChoice.All, persistentSetOf(), emoji),
            protectedContent = true,
            signMessages = null,
            topics = TopicsSetting(enabled = true, tabs = false),
            discussion = DiscussionSetting(chatId = 70, title = "Rust Berlin", canChange = false),
            canUpgrade = false,
        )

    val basicGroup =
        ChatSettingsUiState.Content(
            kind = GroupKind.BasicGroup,
            reactions = ReactionsSetting(ReactionsChoice.None, persistentSetOf(), emoji),
            protectedContent = false,
            signMessages = null,
            topics = null,
            discussion = null,
            canUpgrade = true,
        )

    val upgradeDialog = basicGroup.copy(dialog = ChatSettingsDialog.Upgrade)

    val saving = channel.copy(isSaving = true)

    val pickerGroups =
        DiscussionPicker.Groups(
            persistentListOf(DiscussionGroup(77, "Rust Berlin talk"), DiscussionGroup(78, "Kotlin Meetup")),
        )

    val pickerEmpty = DiscussionPicker.Groups(persistentListOf())

    val error = ChatSettingsUiState.Error(DomainError.Network)
}
