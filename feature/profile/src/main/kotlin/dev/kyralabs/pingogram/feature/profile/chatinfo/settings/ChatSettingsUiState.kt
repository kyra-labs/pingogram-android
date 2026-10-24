package dev.kyralabs.pingogram.feature.profile.chatinfo.settings

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/** What the settings page of a group or channel shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface ChatSettingsUiState {
    data object Loading : ChatSettingsUiState

    data class Error(
        val error: DomainError,
    ) : ChatSettingsUiState

    /**
     * @property reactions which reactions members may add, and the emoji to pick from.
     * @property protectedContent "Restrict saving content", for the owner; null for others.
     * @property signMessages a channel's signatures; null elsewhere.
     * @property topics a supergroup's topics, for the owner; null elsewhere.
     * @property discussion a channel's discussion group, or the channel a group discusses; null where there is none to
     *   show.
     * @property canUpgrade a basic group the user owns, which may become a supergroup.
     * @property isSaving a change is on its way to Telegram.
     * @property dialog the question shown over the page.
     * @property discussionPicker the groups to link as a channel's discussion group, while their sheet is open.
     */
    data class Content(
        val kind: GroupKind,
        val reactions: ReactionsSetting?,
        val protectedContent: Boolean?,
        val signMessages: SignMessagesSetting?,
        val topics: TopicsSetting?,
        val discussion: DiscussionSetting?,
        val canUpgrade: Boolean,
        val isSaving: Boolean = false,
        val dialog: ChatSettingsDialog? = null,
        val discussionPicker: DiscussionPicker? = null,
    ) : ChatSettingsUiState
}

/** Which reactions members may add: every one, some ([chosen] of [available]), or none. */
@Immutable
data class ReactionsSetting(
    val choice: ReactionsChoice,
    val chosen: ImmutableSet<String>,
    val available: ImmutableList<String>,
)

enum class ReactionsChoice { All, Some, None }

@Immutable
data class SignMessagesSetting(
    val sign: Boolean,
    val showProfiles: Boolean,
)

@Immutable
data class TopicsSetting(
    val enabled: Boolean,
    val tabs: Boolean,
)

/**
 * The chat linked to this one: [title] of chat [chatId], or none ([chatId] 0). [canChange]: the user may pick another
 * (a channel's administrators).
 */
@Immutable
data class DiscussionSetting(
    val chatId: Long,
    val title: String?,
    val canChange: Boolean,
)

/** The groups a channel may discuss in, loading, or why they could not be read. */
@Immutable
sealed interface DiscussionPicker {
    data object Loading : DiscussionPicker

    data class Failed(
        val error: DomainError,
    ) : DiscussionPicker

    data class Groups(
        val groups: ImmutableList<DiscussionGroup>,
    ) : DiscussionPicker
}

@Immutable
data class DiscussionGroup(
    val chatId: Long,
    val title: String,
)

/** The destructive questions of the page. */
enum class ChatSettingsDialog { Upgrade, Unlink }

sealed interface ChatSettingsEvent {
    data object Retry : ChatSettingsEvent

    data class ChooseReactions(
        val choice: ReactionsChoice,
    ) : ChatSettingsEvent

    data class ToggleReaction(
        val emoji: String,
    ) : ChatSettingsEvent

    data class SetProtectedContent(
        val on: Boolean,
    ) : ChatSettingsEvent

    data class SetSignMessages(
        val sign: Boolean,
        val showProfiles: Boolean,
    ) : ChatSettingsEvent

    data class SetTopics(
        val enabled: Boolean,
        val tabs: Boolean,
    ) : ChatSettingsEvent

    /** The discussion group's row: its sheet opens. */
    data object OpenDiscussionPicker : ChatSettingsEvent

    data object CloseDiscussionPicker : ChatSettingsEvent

    /** A group picked in the sheet. */
    data class LinkDiscussion(
        val chatId: Long,
    ) : ChatSettingsEvent

    /** "Unlink": asks first. */
    data object Unlink : ChatSettingsEvent

    /** "Upgrade to supergroup": asks first. */
    data object Upgrade : ChatSettingsEvent

    data object ConfirmDialog : ChatSettingsEvent

    data object DismissDialog : ChatSettingsEvent
}

sealed interface ChatSettingsEffect {
    data class ShowError(
        val error: DomainError,
    ) : ChatSettingsEffect

    /** The group became supergroup [chatId], a new chat, which opens. */
    data class Upgraded(
        val chatId: Long,
    ) : ChatSettingsEffect
}
