package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.ChatFolderExcludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderInvite
import dev.kyralabs.pingogram.core.domain.model.ChatFolderInviteLink
import dev.kyralabs.pingogram.core.domain.model.ChatFolderSettings
import dev.kyralabs.pingogram.core.domain.model.RecommendedChatFolder
import org.drinkless.tdlib.TdApi

// td_api.tl's chatFolder and the objects around it, to and from core:domain.

internal fun TdApi.ChatFolder.toChatFolderSettings(): ChatFolderSettings =
    ChatFolderSettings(
        title = name.text.toFormattedText(),
        // td_api.tl: "may be null"; then Telegram chooses one from the folder's chats.
        iconName = icon?.name?.takeIf { it.isNotEmpty() },
        colorId = colorId.takeIf { it >= 0 },
        isShareable = isShareable,
        pinnedChatIds = pinnedChatIds.toList(),
        includedChatIds = includedChatIds.toList(),
        excludedChatIds = excludedChatIds.toList(),
        includedTypes =
            buildSet {
                if (includeContacts) add(ChatFolderIncludedType.Contacts)
                if (includeNonContacts) add(ChatFolderIncludedType.NonContacts)
                if (includeGroups) add(ChatFolderIncludedType.Groups)
                if (includeChannels) add(ChatFolderIncludedType.Channels)
                if (includeBots) add(ChatFolderIncludedType.Bots)
            },
        excludedTypes =
            buildSet {
                if (excludeMuted) add(ChatFolderExcludedType.Muted)
                if (excludeRead) add(ChatFolderExcludedType.Read)
                if (excludeArchived) add(ChatFolderExcludedType.Archived)
            },
    )

/**
 * The folder for `createChatFolder` and `editChatFolder`. A pinned chat is in the folder anyway (td_api.tl: "pinned and
 * always included"), so it is sent once, as pinned, and not again among the included chats.
 */
internal fun ChatFolderSettings.toTdChatFolder(): TdApi.ChatFolder =
    TdApi.ChatFolder(
        TdApi.ChatFolderName(title.toTdFormattedText(), true),
        iconName?.let { TdApi.ChatFolderIcon(it) },
        colorId ?: -1,
        isShareable,
        pinnedChatIds.toLongArray(),
        includedChatIds.filterNot { it in pinnedChatIds }.toLongArray(),
        excludedChatIds.toLongArray(),
        ChatFolderExcludedType.Muted in excludedTypes,
        ChatFolderExcludedType.Read in excludedTypes,
        ChatFolderExcludedType.Archived in excludedTypes,
        ChatFolderIncludedType.Contacts in includedTypes,
        ChatFolderIncludedType.NonContacts in includedTypes,
        ChatFolderIncludedType.Bots in includedTypes,
        ChatFolderIncludedType.Groups in includedTypes,
        ChatFolderIncludedType.Channels in includedTypes,
    )

internal fun TdApi.RecommendedChatFolder.toRecommendedChatFolder(): RecommendedChatFolder =
    RecommendedChatFolder(settings = folder.toChatFolderSettings(), description = description)

internal fun TdApi.ChatFolderInviteLink.toChatFolderInviteLink(): ChatFolderInviteLink =
    ChatFolderInviteLink(link = inviteLink, name = name, chatIds = chatIds.toList())

internal fun TdApi.ChatFolderInviteLinkInfo.toChatFolderInvite(): ChatFolderInvite =
    ChatFolderInvite(
        folder = chatFolderInfo.toChatFolder(),
        missingChatIds = missingChatIds.toList(),
        addedChatIds = addedChatIds.toList(),
    )
