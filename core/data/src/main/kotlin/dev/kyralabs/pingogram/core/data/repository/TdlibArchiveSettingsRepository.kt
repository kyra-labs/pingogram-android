package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toTdChatList
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettings
import dev.kyralabs.pingogram.core.domain.model.ArchiveSettingsAvailability
import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.repository.ArchiveSettingsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ArchiveSettingsRepository] on TDLib's `getArchiveChatListSettings`, `setArchiveChatListSettings` and
 * `readChatList`. TDLib sends no update for the archive's settings, so nothing is cached and this is no
 * `TdlibUpdateCache`: each read asks again. Whether new chats may be archived is TDLib's option
 * `can_archive_and_mute_new_chats_from_unknown_users`, which it answers from memory.
 */
@AccountScoped
class TdlibArchiveSettingsRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ArchiveSettingsRepository {
        override suspend fun archiveSettings(): Outcome<ArchiveSettingsAvailability> {
            val settings =
                when (val result = client.request(TdApi.GetArchiveChatListSettings())) {
                    is Outcome.Success -> result.value
                    is Outcome.Failure -> return result
                }
            // An option TDLib does not know is empty, which means no; so does an option that cannot be read.
            val canArchive =
                when (val option = client.request(TdApi.GetOption(CAN_ARCHIVE_AND_MUTE_NEW_CHATS))) {
                    is Outcome.Success -> (option.value as? TdApi.OptionValueBoolean)?.value == true
                    is Outcome.Failure -> false
                }
            return Outcome.Success(ArchiveSettingsAvailability(settings.toArchiveSettings(), canArchive))
        }

        override suspend fun setArchiveSettings(settings: ArchiveSettings): Outcome<Unit> =
            client
                .request(
                    TdApi.SetArchiveChatListSettings(
                        TdApi.ArchiveChatListSettings(
                            settings.archiveAndMuteNewChatsFromNonContacts,
                            settings.keepUnmutedChatsArchived,
                            settings.keepChatsFromFoldersArchived,
                        ),
                    ),
                ).map {}

        override suspend fun readChatList(list: ChatListType): Outcome<Unit> =
            client.request(TdApi.ReadChatList(list.toTdChatList())).map {}

        private companion object {
            const val CAN_ARCHIVE_AND_MUTE_NEW_CHATS = "can_archive_and_mute_new_chats_from_unknown_users"
        }
    }

private fun TdApi.ArchiveChatListSettings.toArchiveSettings() =
    ArchiveSettings(
        archiveAndMuteNewChatsFromNonContacts = archiveAndMuteNewChatsFromUnknownUsers,
        keepUnmutedChatsArchived = keepUnmutedChatsArchived,
        keepChatsFromFoldersArchived = keepChatsFromFoldersArchived,
    )
