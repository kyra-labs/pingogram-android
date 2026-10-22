package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toAnimation
import dev.kyralabs.pingogram.core.data.mapper.toPhoto
import dev.kyralabs.pingogram.core.domain.model.BotCommand
import dev.kyralabs.pingogram.core.domain.model.BotInfo
import dev.kyralabs.pingogram.core.domain.model.BotMenuButton
import dev.kyralabs.pingogram.core.domain.model.ChatBots
import dev.kyralabs.pingogram.core.domain.repository.BotInfoRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BotInfoRepository] on TDLib: a chat's bots from the full info of its user or group, followed through
 * `updateUserFullInfo`, `updateBasicGroupFullInfo` and `updateSupergroupFullInfo` while collected, and
 * `sendBotStartMessage`. Nothing is cached, so it is no `TdlibUpdateCache`: TDLib keeps the full info itself.
 *
 * - **Which full info**: `getChat` says what the chat is. A private chat asks `getUser` (from memory) whether its user
 *   is a bot, and only a bot's `getUserFullInfo` (`bot_info`); a basic group or a supergroup asks for its full info
 *   (`bot_commands`, one entry per bot). Channels, whose members send nothing, secret chats (bots have none) and chats
 *   with people ask for nothing more: [ChatBots.None].
 * - **Order**: the updates are followed before the full info is asked for, so a change in between is not missed; TDLib
 *   sends an update before the answer it causes.
 * - **Commands** without a name (a newer TDLib's ephemeral ones have one too) are left out.
 *
 * Nothing about a bot (its description, its menu button's address) is logged.
 */
@Singleton
class TdlibBotInfoRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : BotInfoRepository {
        override fun chatBots(chatId: Long): Flow<Outcome<ChatBots>> =
            channelFlow {
                val chat =
                    when (val found = client.request(TdApi.GetChat(chatId))) {
                        is Outcome.Success -> {
                            found.value
                        }

                        is Outcome.Failure -> {
                            send(found)
                            return@channelFlow
                        }
                    }
                when (val type = chat.type) {
                    is TdApi.ChatTypePrivate -> privateChatBot(type.userId)
                    is TdApi.ChatTypeBasicGroup -> basicGroupBots(type.basicGroupId)
                    is TdApi.ChatTypeSupergroup -> if (type.isChannel) none() else supergroupBots(type.supergroupId)
                    else -> none()
                }
            }

        override suspend fun sendStartMessage(
            botUserId: Long,
            chatId: Long,
            parameter: String,
        ): Outcome<Unit> = client.request(TdApi.SendBotStartMessage(botUserId, chatId, parameter)).map { }

        /** The bot of a private chat with user [userId]; nothing for a person. */
        private suspend fun ProducerScope<Outcome<ChatBots>>.privateChatBot(userId: Long) {
            val user =
                when (val found = client.request(TdApi.GetUser(userId))) {
                    is Outcome.Success -> {
                        found.value
                    }

                    is Outcome.Failure -> {
                        send(found)
                        return
                    }
                }
            if (user.type !is TdApi.UserTypeBot) return none()
            follow(
                changes = { update ->
                    (update as? TdApi.UpdateUserFullInfo)
                        ?.takeIf { it.userId == userId }
                        ?.userFullInfo
                        ?.toChatBots(userId)
                },
                request = { client.request(TdApi.GetUserFullInfo(userId)).map { it.toChatBots(userId) } },
            )
        }

        private suspend fun ProducerScope<Outcome<ChatBots>>.basicGroupBots(basicGroupId: Long) =
            follow(
                changes = { update ->
                    if (update is TdApi.UpdateBasicGroupFullInfo && update.basicGroupId == basicGroupId) {
                        update.basicGroupFullInfo?.botCommands.toChatBots()
                    } else {
                        null
                    }
                },
                request = {
                    client.request(TdApi.GetBasicGroupFullInfo(basicGroupId)).map { it.botCommands.toChatBots() }
                },
            )

        private suspend fun ProducerScope<Outcome<ChatBots>>.supergroupBots(supergroupId: Long) =
            follow(
                changes = { update ->
                    if (update is TdApi.UpdateSupergroupFullInfo && update.supergroupId == supergroupId) {
                        update.supergroupFullInfo?.botCommands.toChatBots()
                    } else {
                        null
                    }
                },
                request = {
                    client.request(TdApi.GetSupergroupFullInfo(supergroupId)).map { it.botCommands.toChatBots() }
                },
            )

        /**
         * Follows the updates [changes] reads (null for other updates), from before [request] goes out, and sends its
         * answer, then each change.
         */
        private suspend fun ProducerScope<Outcome<ChatBots>>.follow(
            changes: (TdApi.Update) -> ChatBots?,
            request: suspend () -> Outcome<ChatBots>,
        ) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                client.updates.collect { update -> changes(update)?.let { send(Outcome.Success(it)) } }
            }
            send(request())
        }

        private suspend fun ProducerScope<Outcome<ChatBots>>.none() = send(Outcome.Success(ChatBots.None))
    }

/** A bot's full info as its chat's bots: its commands, and what it shows before the first message. */
private fun TdApi.UserFullInfo.toChatBots(botUserId: Long): ChatBots {
    val info = botInfo ?: return ChatBots.None
    return ChatBots(
        commands = info.commands.toCommands(botUserId),
        bot =
            BotInfo(
                botUserId = botUserId,
                description = info.description.orEmpty().trim(),
                photo = info.photo?.toPhoto(),
                animation = info.animation?.toAnimation(),
                // td_api.tl: a Web App's address; without one the menu is the bot's commands.
                menuButton = info.menuButton?.takeIf { it.url.isNotBlank() }?.let { BotMenuButton(it.text, it.url) },
            ),
    )
}

/** A group's bots' commands, bot by bot; none for a group without bots. */
private fun Array<TdApi.BotCommands?>?.toChatBots(): ChatBots {
    val commands = orEmpty().filterNotNull().flatMap { it.commands.toCommands(it.botUserId) }
    return if (commands.isEmpty()) ChatBots.None else ChatBots(commands.toImmutableList(), bot = null)
}

private fun Array<TdApi.BotCommand?>?.toCommands(botUserId: Long) =
    orEmpty()
        .filterNotNull()
        .filter { it.command.isNotBlank() }
        .map { BotCommand(it.command, it.description.orEmpty(), botUserId) }
        .toImmutableList()
