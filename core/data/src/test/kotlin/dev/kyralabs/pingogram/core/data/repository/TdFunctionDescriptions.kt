package dev.kyralabs.pingogram.core.data.repository

import org.drinkless.tdlib.TdApi

/**
 * A readable description of a sent function, to compare sequences of requests: TdApi classes have no `equals`, and
 * their `toString()` is native.
 */
internal fun TdApi.Function<*>.describe(): String =
    when (this) {
        is TdApi.GetChat -> {
            "GetChat($chatId)"
        }

        is TdApi.GetBasicGroup -> {
            "GetBasicGroup($basicGroupId)"
        }

        is TdApi.GetSupergroup -> {
            "GetSupergroup($supergroupId)"
        }

        is TdApi.ToggleChatIsPinned -> {
            "ToggleChatIsPinned(${chatList.describe()}, $chatId, isPinned=$isPinned)"
        }

        is TdApi.AddChatToList -> {
            "AddChatToList($chatId, ${chatList.describe()})"
        }

        is TdApi.ToggleChatIsMarkedAsUnread -> {
            "ToggleChatIsMarkedAsUnread($chatId, $isMarkedAsUnread)"
        }

        is TdApi.ViewMessages -> {
            "ViewMessages($chatId, ${messageIds.toList()}, ${source.name()}, forceRead=$forceRead)"
        }

        is TdApi.ReadAllChatMentions -> {
            "ReadAllChatMentions($chatId)"
        }

        is TdApi.ReadAllChatReactions -> {
            "ReadAllChatReactions($chatId)"
        }

        is TdApi.DeleteChatHistory -> {
            "DeleteChatHistory($chatId, removeFromChatList=$removeFromChatList, revoke=$revoke)"
        }

        is TdApi.DeleteChat -> {
            "DeleteChat($chatId)"
        }

        is TdApi.LeaveChat -> {
            "LeaveChat($chatId)"
        }

        is TdApi.CloseSecretChat -> {
            "CloseSecretChat($secretChatId)"
        }

        else -> {
            javaClass.simpleName
        }
    }

private fun TdApi.ChatList.describe(): String =
    when (this) {
        is TdApi.ChatListFolder -> "Folder($chatFolderId)"
        else -> javaClass.simpleName.removePrefix("ChatList")
    }

private fun TdApi.Object?.name(): String = this?.javaClass?.simpleName ?: "null"
