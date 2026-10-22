package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageAutoDelete
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import dev.kyralabs.pingogram.core.domain.model.UsernameProblem
import dev.kyralabs.pingogram.feature.chats.ui.create.ChatPhoto
import dev.kyralabs.pingogram.feature.chats.ui.create.FloodWait
import dev.kyralabs.pingogram.feature.chats.ui.create.InviteLinkState
import dev.kyralabs.pingogram.feature.chats.ui.create.MemberItem
import dev.kyralabs.pingogram.feature.chats.ui.create.MemberList
import dev.kyralabs.pingogram.feature.chats.ui.create.MemberPickerUiState
import dev.kyralabs.pingogram.feature.chats.ui.create.NewChannelDetailsUiState
import dev.kyralabs.pingogram.feature.chats.ui.create.NewChannelTypeUiState
import dev.kyralabs.pingogram.feature.chats.ui.create.NewGroupDetailsUiState
import dev.kyralabs.pingogram.feature.chats.ui.create.NotAddedDialog
import dev.kyralabs.pingogram.feature.chats.ui.create.UsernameStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Sample states of creating a group or a channel, shared by the previews and the screenshot tests: picking people
 * (some picked, a search without matches, loading, no contacts, an error), the group's details (empty, filled in with
 * a missing name, creating, a flood wait, people not added), the channel's details, and its type (private with its
 * link, public while checking, available, taken, a username that cannot be one).
 */
internal object CreateChatPreviewData {
    private val people =
        listOf(
            member(2, "Ada Lovelace", username = "ada", isOnline = true),
            member(11, "Alan Turing", username = "turing"),
            member(4, "Grace Hopper"),
            member(12, "Hedy Lamarr", username = "hedy"),
            member(13, "Katherine Johnson"),
            member(14, "Margaret Hamilton", username = "margaret", isOnline = true),
        )

    private fun member(
        id: Long,
        name: String,
        username: String? = null,
        isOnline: Boolean = false,
    ) = MemberItem(userId = id, name = name, photo = null, isOnline = isOnline, username = username, isPicked = false)

    private val pickedIds = setOf(2L, 4L)

    val picking =
        MemberPickerUiState(
            contacts = MemberList.Content(people.map { it.copy(isPicked = it.userId in pickedIds) }.toImmutableList()),
            picked = people.filter { it.userId in pickedIds }.map { it.copy(isPicked = true) }.toImmutableList(),
        )

    val noneMatching = MemberPickerUiState(query = "Zed", contacts = MemberList.NoResults)

    val contactsLoading = MemberPickerUiState()

    val noContacts = MemberPickerUiState(contacts = MemberList.Empty)

    val contactsError = MemberPickerUiState(contacts = MemberList.Error(DomainError.Network))

    val groupEmpty = NewGroupDetailsUiState(memberCount = 2)

    val groupFilled = NewGroupDetailsUiState(title = "Book club", autoDelete = MessageAutoDelete.Week, memberCount = 2)

    val groupMissingName = NewGroupDetailsUiState(memberCount = 0, titleError = true)

    val groupCreating = groupFilled.copy(isCreating = true)

    val groupFloodWait = groupFilled.copy(floodWait = FloodWait(seconds = 42, totalSeconds = 60))

    val groupPreparingPhoto = groupFilled.copy(photo = ChatPhoto.Preparing)

    val groupNotAdded =
        groupFilled.copy(
            notAdded = NotAddedDialog(chatId = -5, count = 2, names = persistentListOf("Alan Turing", "Hedy Lamarr")),
        )

    val channelEmpty = NewChannelDetailsUiState()

    val channelFilled =
        NewChannelDetailsUiState(title = "Pingogram news", description = "What's new in Pingogram, every week.")

    val channelCreating = channelFilled.copy(isCreating = true)

    val channelPrivate = NewChannelTypeUiState(inviteLink = InviteLinkState.Ready("https://t.me/+AbCdEf123456"))

    val channelLinkLoading = NewChannelTypeUiState()

    val channelLinkError = NewChannelTypeUiState(inviteLink = InviteLinkState.Error(DomainError.Network))

    val channelChecking =
        NewChannelTypeUiState(isPublic = true, username = "pingogram_news", usernameStatus = UsernameStatus.Checking)

    val channelAvailable =
        channelChecking.copy(usernameStatus = UsernameStatus.Checked(UsernameCheck.Available))

    val channelTaken = channelChecking.copy(usernameStatus = UsernameStatus.Checked(UsernameCheck.Taken))

    val channelTooShort =
        NewChannelTypeUiState(
            isPublic = true,
            username = "png",
            usernameStatus = UsernameStatus.Problem(UsernameProblem.TooShort),
        )
}

/** Every state of the steps of creating a group or a channel for their previews, named in the preview pane. */
internal object CreateChatPreviewStates {
    class Members :
        Named<MemberPickerUiState>(
            "Picking" to CreateChatPreviewData.picking,
            "No matches" to CreateChatPreviewData.noneMatching,
            "Loading" to CreateChatPreviewData.contactsLoading,
            "No contacts" to CreateChatPreviewData.noContacts,
            "Error" to CreateChatPreviewData.contactsError,
        )

    class GroupDetails :
        Named<NewGroupDetailsUiState>(
            "Empty" to CreateChatPreviewData.groupEmpty,
            "Filled in" to CreateChatPreviewData.groupFilled,
            "Missing name" to CreateChatPreviewData.groupMissingName,
            "Creating" to CreateChatPreviewData.groupCreating,
            "Flood wait" to CreateChatPreviewData.groupFloodWait,
            "Preparing photo" to CreateChatPreviewData.groupPreparingPhoto,
            "Not added" to CreateChatPreviewData.groupNotAdded,
        )

    class ChannelDetails :
        Named<NewChannelDetailsUiState>(
            "Empty" to CreateChatPreviewData.channelEmpty,
            "Filled in" to CreateChatPreviewData.channelFilled,
            "Creating" to CreateChatPreviewData.channelCreating,
        )

    class ChannelType :
        Named<NewChannelTypeUiState>(
            "Private" to CreateChatPreviewData.channelPrivate,
            "Link loading" to CreateChatPreviewData.channelLinkLoading,
            "Link error" to CreateChatPreviewData.channelLinkError,
            "Checking" to CreateChatPreviewData.channelChecking,
            "Available" to CreateChatPreviewData.channelAvailable,
            "Taken" to CreateChatPreviewData.channelTaken,
            "Too short" to CreateChatPreviewData.channelTooShort,
        )

    /** States named in the preview pane. */
    open class Named<T>(
        private vararg val states: Pair<String, T>,
    ) : PreviewParameterProvider<T> {
        override val values: Sequence<T> = states.asSequence().map { it.second }

        override fun getDisplayName(index: Int): String = states[index].first
    }
}
